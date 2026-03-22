# E2E Test Fixtures

This directory contains test fixtures for E2E tests.

## Files

### `sample-report.png`

**Note:** This is a placeholder. Replace with an actual health report image for OCR testing.

To create a proper test image:

1. Take a screenshot of a sample health examination report
2. Ensure it contains recognizable health metrics like:
   - Blood glucose values (e.g., "Blood Glucose: 5.6 mmol/L")
   - Blood pressure readings (e.g., "Blood Pressure: 120/80 mmHg")
   - Heart rate (e.g., "Heart Rate: 72 bpm")
   - Body temperature
   - Weight
3. Save as `sample-report.png` in this directory

**Important:** Do not include real personal health information in test fixtures.

### `invalid.txt`

A text file used to test file type validation in the OCR upload component.
The OCR component should only accept image files (JPG, PNG), so uploading
this file should trigger an error message.