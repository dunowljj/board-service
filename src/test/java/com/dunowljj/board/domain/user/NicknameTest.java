package com.dunowljj.board.domain.user;

import com.dunowljj.board.common.error.InvalidUserContentException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameTest {

    @Test
    @DisplayName("display 는 trim + NFC 결합 형태로 저장된다")
    void display_normalizes_with_NFC_and_trim() {
        Nickname nickname = new Nickname("  Alice  ");
        assertThat(nickname.display()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("canonical 은 trim + NFKC + lower-case 로 저장된다")
    void canonical_normalizes_with_NFKC_lowercase_and_trim() {
        Nickname nickname = new Nickname("  Alice  ");
        assertThat(nickname.canonical()).isEqualTo("alice");
    }

    @Test
    @DisplayName("대소문자 차이만 있는 두 닉네임은 같은 canonical 을 갖는다")
    void case_variants_share_canonical() {
        Nickname a = new Nickname("Alice");
        Nickname b = new Nickname("alice");
        assertThat(a.canonical()).isEqualTo(b.canonical());
        assertThat(a.display()).isNotEqualTo(b.display());
    }

    @Test
    @DisplayName("한글 닉네임도 정규화된다")
    void hangul_is_accepted() {
        Nickname nickname = new Nickname("관리자");
        assertThat(nickname.display()).isEqualTo("관리자");
        assertThat(nickname.canonical()).isEqualTo("관리자");
    }

    @Test
    @DisplayName("길이가 2 미만이면 InvalidUserContentException")
    void rejects_too_short() {
        assertThatThrownBy(() -> new Nickname("a"))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("길이가 20 초과이면 InvalidUserContentException")
    void rejects_too_long() {
        assertThatThrownBy(() -> new Nickname("a".repeat(21)))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("null 이면 InvalidUserContentException")
    void rejects_null() {
        assertThatThrownBy(() -> new Nickname(null))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("허용 안 된 문자 (공백 / 특수문자) 이면 InvalidUserContentException")
    void rejects_disallowed_characters() {
        assertThatThrownBy(() -> new Nickname("alice bob"))
                .isInstanceOf(InvalidUserContentException.class);
        assertThatThrownBy(() -> new Nickname("alice@bob"))
                .isInstanceOf(InvalidUserContentException.class);
    }

    @Test
    @DisplayName("equals 와 hashCode 는 canonical 기준으로 동작한다")
    void equals_and_hashCode_contract() {
        EqualsVerifier.forClass(Nickname.class)
                .withOnlyTheseFields("canonical")
                .verify();
    }

    @Test
    @DisplayName("Nickname.isValidDisplay 정책 메서드를 직접 호출 — VO·validator 공유 단일 출처 계약 고정")
    void isValidDisplay_policy_method_contract() {
        assertThat(Nickname.isValidDisplay("alice")).isTrue();
        assertThat(Nickname.isValidDisplay("관리자")).isTrue();
        assertThat(Nickname.isValidDisplay("a")).isFalse();            // 너무 짧음
        assertThat(Nickname.isValidDisplay("a".repeat(21))).isFalse(); // 너무 김
        assertThat(Nickname.isValidDisplay("alice bob")).isFalse();    // 공백
        assertThat(Nickname.isValidDisplay("alice@bob")).isFalse();    // 특수문자
        assertThat(Nickname.isValidDisplay(null)).isFalse();
    }
}
