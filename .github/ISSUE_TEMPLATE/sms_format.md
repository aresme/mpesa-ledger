---
name: New SMS format
about: Report an unparsed M-Pesa or financial SMS format
title: '[SMS] '
labels: parser
assignees: ''
---

**Message source**
Which sender? [e.g. MPESA, Safaricom, GlobalPay, etc.]

**Message type**
What kind of transaction? [e.g. sent money, received, withdrawal, loan, etc.]

**Masked message**
Provide the message with sensitive data masked:

**IMPORTANT: Replace all personal information:**
- Names → "NAME"
- Phone numbers → "254XXXXXXXXX"
- Transaction codes → "XXXX000000"
- Keep the exact structure and wording

Example:
```
XXXX000000 Confirmed. Ksh1,234.56 sent to NAME 254XXXXXXXXX on 1/1/24 at 10:00 AM. New M-PESA balance is Ksh9,876.54. Transaction cost, Ksh12.00.
```

**Your masked message:**
```
[Paste here]
```

**Current behavior**
Where does this message appear in the app?
- [ ] Unrecognized messages
- [ ] Not imported at all
- [ ] Imported but parsed incorrectly

**Expected behavior**
How should this message be categorized and displayed?

**Additional context**
- When did you first notice this format?
- Is this a recent change from the sender?
- Any other relevant information
