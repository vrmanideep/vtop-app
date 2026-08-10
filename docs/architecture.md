# VTOP App 2.0 Architecture

**Status:** Draft v1.0

---

# Vision

VTOP App is no longer a timetable application.

It is a complete academic companion built around the VTOP ecosystem.

The application should be designed such that:

- Every feature reuses existing infrastructure.
- Authentication exists in exactly one place.
- WebView is only a renderer.
- Sync is centralized.
- Every module has a single responsibility.
- Future features (Registration Assistant, AI, Automation, etc.) can be built without changing the architecture.

---

# Current Problems

As the project has grown, several architectural issues have emerged.

## 1. Session Ownership

Authentication is handled from multiple places.

Examples include:

- GlobalSyncer
- VtopSyncWorker
- Portal
- LoginActivity

This makes session handling difficult to maintain.

---

## 2. WebView owns Portal State

Currently:

WebView
↓

Portal

If Android destroys the renderer, the portal is lost.

---

## 3. GlobalSyncer has become a God Object

GlobalSyncer currently:

- logs in
- downloads timetable
- downloads attendance
- downloads marks
- downloads outings
- downloads exams
- updates AppBridge
- writes Vault
- updates widgets
- syncs calendar

It performs orchestration, networking and state management simultaneously.

---

## 4. AppBridge is becoming global storage

AppBridge currently stores many unrelated pieces of state.

Over time this becomes difficult to maintain.

---

## 5. No central navigation abstraction

Modules cannot simply say

Open Attendance in Portal

Instead they manually create navigation.

---

## 6. Portal is treated as a screen

Portal should instead be treated as a service.

---

# Design Principles

## Single Responsibility

Every manager should own exactly one responsibility.

Examples:

SessionManager

owns authentication.

PortalController

owns portal navigation.

AttendanceSyncEngine

owns attendance synchronization.

Nothing else.

---

## Infrastructure before Features

Every new feature should build on existing infrastructure.

Never duplicate:

- login
- cookies
- navigation
- downloads
- session storage

---

## WebView is Disposable

The WebView is not the portal.

It is only a renderer.

The portal should survive WebView recreation.

---

## Business Logic never depends on UI

Attendance synchronization should never know anything about Compose.

Portal automation should never know anything about screens.

---

# Target Architecture

                    VTOP APP
                        │
        ┌───────────────┼────────────────┐
        │               │                │
        ▼               ▼                ▼
     UI Layer      Domain Layer     Infrastructure
        │               │                │
        ▼               ▼                ▼
    Timetable      Attendance       SessionManager
    Attendance     Faculty          PortalController
    Marks          Exams            SyncManager
    Calendar       Outings          DownloadManager
    Simulator                       EventBus

---

# Modules

## core/

Application infrastructure.

Contains

- SessionManager
- NavigationManager
- EventBus
- Preferences
- Repositories

No feature-specific code.

---

## network/

Pure networking.

Contains

- VtopClient
- request builders
- parsers
- API constants

No UI.

No synchronization.

---

## portal/

Everything related to VTOP browsing.

Contains

PortalController

PortalHost

JavascriptBridge

DownloadManager

PortalState

WebView lifecycle

---

## sync/

Synchronization only.

Contains

SyncManager

AttendanceSyncEngine

MarksSyncEngine

FacultySyncEngine

ExamSyncEngine

TimetableSyncEngine

CalendarSyncEngine

Workers

---

## academic/

Business modules.

Attendance

Marks

Timetable

Faculty

Exams

Calendar

Simulator

These modules never directly touch WebView.

---

## ui/

Compose screens only.

Contains

screens

components

dialogs

animations

No networking.

---

## automation/

Future.

Registration Assistant

Macros

Portal automation

AI

---

# SessionManager

Responsible for

- Login
- Logout
- Session refresh
- Cookies
- Active VtopClient
- Semester
- Authorized ID

Public API

login()

logout()

refresh()

client()

state()

No other class performs authentication.

---

# PortalController

Responsible for

Current page

Navigation

Deep links

Portal commands

Public API

openHome()

openAttendance()

openMarks()

openFaculty()

openRegistration()

reload()

executeJavascript()

The controller contains no WebView.

---

# PortalHost

Owns the WebView.

Responsibilities

Create WebView

Destroy WebView

Restore WebView

Handle renderer crashes

Attach to PortalController

---

# SyncManager

Responsible for coordinating synchronization.

Workflow

SyncManager

↓

Attendance

Marks

Timetable

Faculty

Exams

Calendar

Instead of GlobalSyncer performing everything.

---

# EventBus

Application events.

Examples

SessionExpired

AttendanceUpdated

PortalReloaded

CalendarExportFinished

FacultyDatabaseUpdated

SemesterChanged

Modules subscribe.

Modules never poll.

---

# Data Ownership

Attendance

↓

AttendanceRepository

Timetable

↓

TimetableRepository

Marks

↓

MarksRepository

No global AppBridge state where possible.

---

# AppBridge Migration

Current

AppBridge

↓

Everything

Target

SessionManager

AttendanceRepository

PortalController

NavigationManager

ThemeManager

AppBridge should eventually disappear or become a thin compatibility layer.

---

# Portal Lifecycle

Current

Open Portal

↓

Create WebView

↓

Done

Target

PortalController

↓

PortalHost

↓

WebView

↓

PortalController

If renderer dies

↓

Create new WebView

↓

Restore page

↓

Continue

---

# Deep Links

Examples

Attendance

↓

Open in Portal

Faculty

↓

Open in Portal

Course Registration

↓

Open in Portal

No manual navigation.

---

# Dependency Rules

UI

↓

Repositories

↓

Managers

↓

Network

Never the opposite.

Compose should never directly call VtopClient.

---

# Migration Plan

## Phase 1

SessionManager

Move authentication.

No UI changes.

---

## Phase 2

PortalController

PortalHost

Restore WebView lifecycle.

---

## Phase 3

SyncManager

Move orchestration from GlobalSyncer.

Keep existing engines.

---

## Phase 4

Repositories

Split AppBridge.

Introduce repositories.

---

## Phase 5

Deep links

Portal shortcuts

Navigation abstraction.

---

## Phase 6

Automation

Registration Assistant

Macros

AI

---

# Success Criteria

The architecture is considered complete when

- Only SessionManager performs authentication.
- WebView can be destroyed and recreated without losing portal state.
- GlobalSyncer becomes SyncManager.
- Business modules do not know about WebView.
- AppBridge is no longer global storage.
- Every future feature reuses existing infrastructure.