# Command: Code Review

Use when:
- Reviewing code changes before merge
- Verifying architectural alignment
- Checking for potential issues

---

## Review Checklist

### Correctness
- Does the code do what it claims?
- Are edge cases handled?
- Are there potential bugs or race conditions?

### Architecture Alignment
- Are Port/Adapter boundaries respected?
- Does Domain remain free of framework dependencies?
- Are dependencies flowing inward only?

### Code Quality
- Is the code readable and maintainable?
- Are naming conventions consistent?
- Is there unnecessary complexity?

### Testing
- Are new behaviors covered by tests?
- Do existing tests still pass?
- Are test boundaries appropriate (unit vs integration)?

### Security
- No secrets or credentials in code
- Input validation at system boundaries
- OWASP top 10 considerations

## Output Template

### Summary
One-line summary of what the change does.

### Findings
- **[Critical]** Must fix before merge
- **[Suggestion]** Improvement recommendation
- **[Question]** Needs clarification

### Verdict
Approve | Request Changes | Needs Discussion
