# ACME salary management — requirements

**Goal.** Give ACME's HR manager one place to maintain current base salaries for 10,000 employees and answer how base pay varies across comparable groups.

**User and workflow.** HR searches by employee name or ID, filters by country and department, opens a record, and records a new annual base salary with a reason. HR can review the prior amount, new amount, timestamp, and editor in salary history. The employee roster is supplied by a repeatable synthetic seed for this assessment.

**Scope and acceptance**

- The directory supports server-side filtering and pagination. HR can find an employee without loading the full roster.
- Each employee has a work country, department, and pay currency. Salaries are positive annual gross base amounts stored as integer minor units. The editor shows the employee's currency, validates the amount, and rejects stale edits. The salary and history entry commit together.
- Pay insights answer “How many employees are in this group?” and “What are the median, average, lowest, and highest salaries?” by currency for the selected country and department. Insights use all matching employees. No cross-currency total or comparison is implied.
- A demo HR account protects the directory, editor, and API. The demo starts with exactly 10,000 synthetic employees and demonstrates search, update, history, and insights. Core validation, persistence, and reporting have deterministic automated tests.

**Deliberately out of scope.** Payroll, taxes, bonuses, and equity involve separate compensation rules. FX conversion requires a rate source and policy. Excel migration requires real file samples and a reconciliation workflow. Hiring, departures, identity federation, approval chains, and backdated pay are separate workflows. This release focuses on current pay and a traceable correction path for one HR role.

**Delivery.** Supply the Git history, setup and deployment instructions, a working web demo, and a short recording of the HR workflow.

