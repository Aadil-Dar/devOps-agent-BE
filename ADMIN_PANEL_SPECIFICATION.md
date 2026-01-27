# Admin Panel - Feature Specification & Design Prompt

## 🎯 Overview

Your DevOps Agent Backend has comprehensive admin APIs. This document outlines all features available and provides a detailed prompt for AI to design a complete admin panel.

---

## 📋 Available Backend APIs (Summary)

### 1. **Project Management APIs** (`/api/admin/projects`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/admin/projects/upload` | POST | Create new project |
| `/api/admin/projects` | GET | List all projects |
| `/api/admin/projects/{id}` | GET | Get project details |
| `/api/admin/projects/{id}` | PUT | Update project |
| `/api/admin/projects/{id}/toggle` | PATCH | Enable/Disable project |
| `/api/admin/projects/{id}/validate` | POST | Validate configuration |
| `/api/admin/projects/{id}` | DELETE | Delete project |

### 2. **AI Cost Tracking APIs** (`/api/admin/ai-cost`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/admin/ai-cost/stats` | GET | Current usage statistics |
| `/api/admin/ai-cost/projection` | GET | Monthly cost projection |
| `/api/admin/ai-cost/report` | GET | Detailed cost report |
| `/api/admin/ai-cost/budget-check` | GET | Check budget status |
| `/api/admin/ai-cost/calculate` | POST | Calculate custom costs |
| `/api/admin/ai-cost/reset` | POST | Reset statistics |

### 3. **System Admin APIs** (`/api/admin/system`)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/admin/system/init-db` | POST | Initialize database |
| `/api/admin/system/health` | GET | System health check |

### 4. **DevOps Operations APIs** (Available for monitoring)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/pull-requests` | GET | List GitHub PRs |
| `/api/pipelines/{name}/status` | GET | Pipeline status |
| `/api/logs` | GET | CloudWatch logs |
| `/api/alarms` | GET | CloudWatch alarms |
| `/api/inspector/vulnerabilities` | GET | Security vulnerabilities |
| `/api/insights/{id}/analyze` | POST | AI vulnerability analysis |
| `/api/cluster-logs` | GET | Cluster log analysis |

---

## 🎨 Admin Panel Features

### **Dashboard (Home Page)**

**Purpose**: Overview of entire system

**Key Metrics**:
1. **Project Overview**
   - Total projects count
   - Active projects
   - Disabled projects
   - Recently added

2. **AI Cost Monitor** (Real-time)
   - Current session cost
   - Monthly projection
   - Budget usage percentage
   - Cost trend chart (last 7 days)

3. **System Health**
   - Backend status
   - Database status (DynamoDB)
   - Secrets Manager status
   - Last sync time

4. **Quick Stats**
   - Total API calls (today/week/month)
   - Total vulnerabilities detected
   - Active pull requests
   - Pipeline success rate

### **Module 1: Project Management**

**Purpose**: CRUD operations for multi-project configuration

**Features**:

1. **Project List View**
   - Table with columns:
     - Project Name
     - GitHub (owner/repo)
     - AWS Region
     - Status (Enabled/Disabled toggle)
     - Created Date
     - Actions (Edit, Delete, Validate)
   - Search/Filter bar
   - Sort by name, date, status
   - Pagination (10/25/50/100 per page)

2. **Create Project Form**
   - Project Name (required)
   - GitHub Owner (required)
   - GitHub Repository (required)
   - GitHub Token (required, masked input)
   - AWS Region (dropdown: eu-west-1, us-east-1, etc.)
   - AWS Access Key (optional, masked)
   - AWS Secret Key (optional, masked)
   - Created By (auto-filled with admin name)
   - Submit button
   - Validation messages

3. **Edit Project Modal**
   - Same fields as create
   - Pre-filled with existing data
   - Option to update secrets separately
   - Save & Cancel buttons

4. **Project Details Page**
   - Read-only view of all configuration
   - Secrets shown as ********* (security)
   - Activity log (created, updated, last used)
   - Validate Configuration button
   - Enable/Disable toggle
   - Delete button (with confirmation)

5. **Bulk Actions**
   - Select multiple projects
   - Bulk enable/disable
   - Bulk delete (with confirmation)
   - Export to JSON

### **Module 2: AI Cost Management**

**Purpose**: Monitor and control AI/GPT API costs

**Features**:

1. **Cost Dashboard**
   - Big numbers display:
     - Total Cost (current session)
     - Total Requests
     - Total Tokens (input + output)
     - Average Cost per Request
   
2. **Monthly Projection Card**
   - Projected monthly cost (large number)
   - Projected requests
   - Projected tokens
   - Progress bar (current vs budget)
   - Status indicator (green/yellow/red)

3. **Budget Monitor**
   - Set monthly budget (input field)
   - Current usage vs budget (gauge chart)
   - Alert threshold slider (80%, 90%, 100%)
   - Email notification toggle
   - Remaining budget countdown

4. **Cost Breakdown Charts**
   - Pie chart: Cost by use case
     - Vulnerability Analysis
     - PR Summaries
     - Log Grouping
     - Log Summaries
   - Line chart: Cost trend (last 30 days)
   - Bar chart: Requests per day

5. **Cost Calculator**
   - Input tokens field
   - Output tokens field
   - Requests per month field
   - Calculate button
   - Results:
     - Cost per request
     - Estimated monthly cost
     - Token breakdown

6. **Detailed Report View**
   - Table with columns:
     - Timestamp
     - Use Case
     - Input Tokens
     - Output Tokens
     - Cost
   - Export to CSV/PDF
   - Date range filter

7. **Actions**
   - Reset Statistics button (with confirmation)
   - Download Report (PDF/CSV)
   - Set Budget Alert

### **Module 3: System Administration**

**Purpose**: System-level operations and monitoring

**Features**:

1. **System Status Panel**
   - Service status indicators:
     - ✅ Backend API (running)
     - ✅ DynamoDB (connected)
     - ✅ Secrets Manager (accessible)
     - ✅ GitHub API (authenticated)
     - ✅ AWS Services (authorized)
   - Last health check time
   - Refresh button

2. **Database Management**
   - Initialize Database button
   - Database status
   - Table information:
     - Table name
     - Item count
     - Last modified
   - Backup/Restore options (future)

3. **Logs Viewer**
   - Real-time application logs
   - Filter by level (INFO, WARN, ERROR)
   - Search logs
   - Auto-refresh toggle
   - Download logs

4. **Configuration Settings**
   - AWS Region (global setting)
   - Cache settings (TTL, max size)
   - API rate limits
   - Feature flags (enable/disable features)

### **Module 4: DevOps Monitoring** (Read-only Dashboard)

**Purpose**: Monitor DevOps operations across all projects

**Features**:

1. **Pull Requests Monitor**
   - List of all open PRs (all projects)
   - Columns: PR #, Title, Author, Project, Status, AI Summary
   - Quick link to GitHub
   - Filter by project

2. **Pipelines Monitor**
   - List of all pipelines (all projects)
   - Status: Success, Failed, In Progress
   - Last run time
   - Success rate percentage
   - Quick actions (view details, restart)

3. **Vulnerabilities Dashboard**
   - Total vulnerabilities count
   - Breakdown by severity (Critical, High, Medium, Low)
   - Recent vulnerabilities list
   - AI analysis status
   - Quick remediation actions

4. **Logs Monitor**
   - Cluster logs summary
   - Error patterns
   - Warning patterns
   - Real-time log streaming (optional)
   - Export logs

5. **Alarms Monitor**
   - Active alarms list
   - Alarm history
   - Alarm by severity
   - Acknowledge/Resolve actions

### **Module 5: User Management** (Future Enhancement)

**Purpose**: Manage admin users and permissions

**Features** (Not yet implemented in backend):
- User list
- Add/Edit/Delete users
- Role-based access control
- Audit logs

---

## 🎨 UI/UX Design Guidelines

### **Layout Structure**

```
┌─────────────────────────────────────────────────────────┐
│  Header (Logo, User Menu, Notifications)               │
├─────────┬───────────────────────────────────────────────┤
│         │                                               │
│ Sidebar │           Main Content Area                   │
│         │                                               │
│ - Dashboard                                             │
│ - Projects                                              │
│ - AI Costs                                              │
│ - System                                                │
│ - Monitoring                                            │
│                                                         │
└─────────┴─────────────────────────────────────────────┘
```

### **Design Principles**

1. **Modern & Clean**: Material Design or Ant Design style
2. **Responsive**: Mobile-friendly (Bootstrap grid)
3. **Dark Mode**: Toggle between light/dark themes
4. **Intuitive**: Clear navigation, breadcrumbs
5. **Fast**: Lazy loading, pagination, caching
6. **Accessible**: WCAG 2.1 AA compliant

### **Color Scheme**

- **Primary**: Blue (#1976D2) - Actions, links
- **Success**: Green (#4CAF50) - Active, healthy states
- **Warning**: Orange (#FF9800) - Budget warnings
- **Danger**: Red (#F44336) - Errors, delete actions
- **Info**: Cyan (#00BCD4) - Information cards
- **Neutral**: Gray (#607D8B) - Disabled states

### **Typography**

- **Headings**: Roboto, Bold, 24px/20px/16px
- **Body**: Roboto, Regular, 14px
- **Code/Monospace**: Fira Code, 12px

---

## 🔐 Security Considerations

1. **Authentication**: JWT/OAuth2 (to be implemented)
2. **Authorization**: Role-based access control
3. **Sensitive Data**: 
   - Never display full tokens/keys
   - Use masked inputs (••••••••)
   - Confirm before delete operations
4. **HTTPS**: Always use secure connections
5. **CORS**: Already configured in backend

---

## 📱 Technology Stack Recommendations

### **Frontend Framework**
- **React** (with TypeScript) - Modern, component-based
- **Vue.js** - Simpler, progressive framework
- **Angular** - Enterprise-ready, full-featured

### **UI Component Library**
- **Material-UI (MUI)** - React, comprehensive
- **Ant Design** - React, enterprise design
- **Vuetify** - Vue.js, Material Design
- **PrimeReact/PrimeNG** - Rich component set

### **State Management**
- **Redux Toolkit** - React state management
- **Zustand** - Lightweight React state
- **Pinia** - Vue 3 state management

### **Charts/Visualizations**
- **Recharts** - React charting library
- **Chart.js** - Simple, flexible charts
- **Apache ECharts** - Feature-rich visualizations

### **HTTP Client**
- **Axios** - Promise-based HTTP client
- **React Query** - Data fetching & caching

### **Styling**
- **Tailwind CSS** - Utility-first CSS
- **Styled Components** - CSS-in-JS
- **SASS/SCSS** - CSS preprocessor

---

## 🤖 DETAILED AI PROMPT FOR ADMIN PANEL DESIGN

Copy and paste this prompt to any AI assistant (ChatGPT, Claude, etc.):

---


