DataCapture is a JavaFX desktop application for capturing structured comic intake data alongside camera image records. It is designed for workflows where an operator needs to define intake fields, run capture sessions, associate images with each item, store the data locally, and export the results for downstream processing.

The project is built with **Java 21**, **JavaFX**, **JavaCV/OpenCV**, **JDBC**, **H2**, **PostgreSQL**, **Gson**, and **Apache POI**.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Core Capabilities](#core-capabilities)
- [Technology Stack](#technology-stack)
- [Application Architecture](#application-architecture)
- [Project Structure](#project-structure)
- [Runtime Workflow](#runtime-workflow)
- [Data Model](#data-model)
- [Database Design](#database-design)
- [Configuration](#configuration)
- [Camera Integration](#camera-integration)
- [Export Capabilities](#export-capabilities)
- [Build and Run](#build-and-run)
- [Packaging](#packaging)
- [Development Notes](#development-notes)

---

## Project Overview

ComicDataCapture is organized as a Maven-based Java desktop application. The user interface is built with JavaFX and FXML, while the application logic is separated into application, controller, model, and service packages.

At a high level, the application supports:

1. Starting a comic intake session.
2. Defining or loading capture profiles.
3. Configuring one or more cameras.
4. Capturing structured metadata for each comic or item.
5. Associating captured image paths with intake entries.
6. Persisting sessions, batches, field definitions, item data, and image records.
7. Exporting captured data to CSV, Excel, or PostgreSQL-oriented workflows.

The application is designed to be **local-first** by default through an embedded H2 database, while still allowing PostgreSQL use for shared or enterprise-style deployments.

---

## Core Capabilities

### Session-Based Intake

The application organizes intake work into sessions. A session represents a period of data collection and acts as the top-level grouping for batches, field definitions, entries, and related images.

This makes it possible to separate one intake run from another and preserve historical context for the data captured during each run.

---

### Batch Tracking

Batches provide a way to group captured entries within a session. A batch can represent an item, comic, group of images, or another logical unit of intake depending on the workflow.

Each batch can be associated with:

- A parent session
- An item label
- A creation timestamp
- Captured entries
- Image records

---

### Dynamic Capture Profiles

Capture profiles allow the application to support customizable intake forms instead of relying on one hardcoded set of fields.

Each capture profile can define fields with metadata such as:

- Display label
- Database key
- Field type
- Required flag
- Dropdown options
- Field ordering

This makes the application flexible enough to adapt to different cataloging or intake schemas.

---

### Field Definition Snapshots

When a session starts, the active field definitions can be stored as part of the session history.

This is important because profiles may change over time. By snapshotting the fields used during a session, old sessions remain understandable even if the current profile is modified later.

For example, a session can still be reconstructed even if:

- A field was renamed
- A dropdown option changed
- A required flag was adjusted
- A new field was added after the original session

---

### Camera Capture Workflow

ComicDataCapture uses JavaCV/OpenCV for camera access. This allows the application to interact with cameras through OpenCV-backed capture APIs.

The camera workflow includes:

- Camera discovery
- Camera configuration
- Preview support
- Image path association with entries
- Support for camera-driven intake screens

Camera access is handled through the service layer so UI controllers do not need to directly manage low-level camera APIs.

---

### Local-First Persistence

By default, the project is configured to use an embedded H2 database in PostgreSQL compatibility mode.

The local database is created automatically under the user's home directory:
~/ComicDataCapture/data/comic_intake.mv.db

This makes the application easy to run without requiring a separate database server.

---

### PostgreSQL Compatibility

The application also includes PostgreSQL support for workflows that require a dedicated database server.

PostgreSQL can be enabled by updating `db.properties` with a PostgreSQL JDBC URL and credentials.

---

### Export and Handoff

Captured data can be exported for use outside the application.

Supported export targets include:

- CSV
- Excel `.xlsx`
- PostgreSQL-oriented handoff

Exports are designed to include both structured intake fields and image path columns where applicable.

---

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21 |
| UI Layout | FXML |
| Camera Integration | JavaCV / OpenCV |
| Local Database | H2 |
| Server Database | PostgreSQL |
| Database Access | JDBC |
| Serialization | Gson |
| Excel Export | Apache POI |
| UI Utilities | ControlsFX, FormsFX, BootstrapFX |
| Build Tool | Maven |
| Packaging | Maven Shade Plugin |
| Testing | JUnit 5 |

---

## Key Dependencies

The main Maven dependencies are:

| Dependency | Purpose |
|---|---|
| `org.bytedeco:javacv-platform` | JavaCV/OpenCV camera access and native bindings |
| `com.h2database:h2` | Embedded local database |
| `org.postgresql:postgresql` | PostgreSQL JDBC driver |
| `org.apache.poi:poi-ooxml` | Excel `.xlsx` export support |
| `com.google.code.gson:gson` | JSON serialization/deserialization |
| `org.openjfx:javafx-controls` | JavaFX controls |
| `org.openjfx:javafx-fxml` | FXML loading and controller binding |
| `org.openjfx:javafx-swing` | JavaFX/Swing interop support |
| `org.controlsfx:controlsfx` | Additional JavaFX UI controls |
| `com.dlsc.formsfx:formsfx-core` | Form-related UI utilities |
| `org.kordamp.bootstrapfx:bootstrapfx-core` | Bootstrap-inspired JavaFX styling |
| `org.junit.jupiter` | Unit testing |

---

## Application Architecture

The source is organized under:
src/main/java/com/comicdatacapture/comicdatacapture


The project follows a practical layered structure:
app controller model service


---

### `app`

The `app` package contains application startup, shared state, and view navigation support.

Responsibilities include:

- Launching the JavaFX application
- Preparing the main stage
- Managing high-level view transitions
- Holding shared application state
- Providing the packaged application entry point

Important classes include:

| Class | Purpose |
|---|---|
| `Launcher` | Main class used by the shaded packaged JAR |
| `cameraIntakeApp` | JavaFX application entry point |
| `AppState` | Shared runtime state holder |
| `viewManager` | Handles navigation between FXML views |

---

### `controller`

The `controller` package contains JavaFX controllers connected to FXML views.

Responsibilities include:

- Handling UI events
- Reading user input
- Updating controls
- Calling services
- Coordinating the intake workflow
- Moving users between screens

Controllers include:

| Controller | Purpose |
|---|---|
| `cameraConfigController` | Handles camera configuration UI |
| `mainViewController` | Handles the main intake screen |
| `sessionController` | Handles session start/setup behavior |
| `sessionEndController` | Handles session completion/export flow |

---

### `model`

The `model` package contains the core domain objects used by the application.

Models include:

| Model | Purpose |
|---|---|
| `Session` | Represents an intake session |
| `Batch` | Represents a grouped unit of captured work |
| `CameraConfig` | Stores camera-related configuration |
| `CaptureProfile` | Represents a dynamic intake profile |
| `FieldDefinition` | Represents one configurable intake field |
| `CapturedContent` | Represents captured item data and related content |

These classes are the central data structures passed between controllers and services.

---

### `service`

The `service` package contains application logic, persistence operations, camera integration, profile handling, and export behavior.

Services include:

| Service | Purpose |
|---|---|
| `cameraService` | Handles camera discovery/capture integration |
| `cameraPreviewWorker` | Supports live camera preview work |
| `DatabaseService` | Handles database connections and persistence |
| `ExportService` | Handles CSV, Excel, and database-oriented export behavior |
| `ProfileService` | Handles capture profile loading/saving |
| `sessionService` | Handles session and intake workflow persistence |

The service layer keeps controllers focused on UI coordination instead of low-level database, export, or camera details.

---

## Project Structure
WIP


---

## UI Views

FXML files are located under:
src/main/resources/com/comicdatacapture/comicdatacapture


| FXML File | Purpose |
|---|---|
| `intakeLanding-view.fxml` | Landing/start screen for intake |
| `config-view.fxml` | Camera or setup configuration screen |
| `main-view.fxml` | Main intake/capture interface |
| `session-started-view.fxml` | Session-started workflow screen |
| `session-end-view.fxml` | Session completion/export screen |

---

## Runtime Workflow

A typical application flow looks like this:

1. User launches the desktop application.
2. The JavaFX application initializes the main stage.
3. The app prepares camera-related services.
4. User starts or configures an intake session.
5. User selects or defines the capture fields/profile.
6. User configures camera input.
7. User captures item metadata.
8. Image paths are associated with entries.
9. Data is persisted to the configured database.
10. User completes the session.
11. User exports the captured records if needed.

---

## Data Model

The core domain model is centered around sessions, batches, fields, entries, and images.
Session └── Batch ├── Entry / CapturedContent └── Image records



Field definitions are linked to sessions so each session has a durable record of which schema was active during capture.

---

## Database Design

The application schema includes the following main tables:

| Table | Purpose |
|---|---|
| `sessions` | Stores each intake session |
| `batches` | Stores grouped intake batches/items within a session |
| `field_definitions` | Stores the field schema used during a session |
| `entry` | Stores operator-entered field values as JSON/JSONB-style data |
| `images` | Stores image file paths associated with batches or entries |

---

### `sessions`

Stores high-level intake sessions.

Typical columns:

| Column | Purpose |
|---|---|
| `id` | Primary key |
| `started_at` | Timestamp when the session started |

---

### `batches`

Stores grouped work units within a session.

Typical columns:

| Column | Purpose |
|---|---|
| `id` | Primary key |
| `session_id` | Parent session reference |
| `item_label` | Optional item/batch label |
| `created_at` | Batch creation timestamp |

---

### `field_definitions`

Stores the schema active during a session.

Typical columns:

| Column | Purpose |
|---|---|
| `id` | Primary key |
| `session_id` | Parent session reference |
| `field_order` | Display/capture order |
| `label` | Human-readable field label |
| `db_key` | Stable key used in stored entry data |
| `field_type` | Field type such as text, dropdown, boolean, etc. |
| `required` | Whether the field is required |
| `options` | Optional dropdown/list values |

---

### `entry`

Stores captured operator-entered values.

Typical columns:

| Column | Purpose |
|---|---|
| `id` | Primary key |
| `batch_id` | Parent batch reference |
| `captured_at` | Capture timestamp |
| `data` | JSON-style object containing field values |

The `data` column stores flexible key/value intake data. Keys correspond to `db_key` values from `field_definitions`.

Example shape:
json { "series_name": "Batman", "issue_number": "1", "publisher": "DC", "variant": "true" }


---

### `images`

Stores image path records associated with a batch or entry.

Typical columns:

| Column | Purpose |
|---|---|
| `id` | Primary key |
| `batch_id` | Related batch |
| `entry_id` | Related entry |
| `image_type` | Image/category label |
| `file_path` | Path to the captured image |
| `created_at` | Image record creation timestamp |

---

## Database Migration Files

The project contains database schema files for both H2 and PostgreSQL:

src/main/resources/db/migration/h2/V1__schema_h2.sql src/main/resources/db/migration/postgres/V1__schema_postgres.sql


The H2 schema uses H2-compatible types and syntax.

The PostgreSQL schema uses PostgreSQL-native features such as:

- `SERIAL`
- `JSONB`
- GIN index support for entry data

A standalone DDL file is also available:

db/comicIntake.ddl


---

## Configuration

Database configuration is stored in:

db.properties

The default configuration uses embedded H2:
properties db.url=jdbc:h2:~/ComicDataCapture/data/comic_intake;MODE=PostgreSQL db.user=sa db.password=

This creates the local database under:
~/ComicDataCapture/data/

---

### Switching to PostgreSQL

To use PostgreSQL instead of H2, update `db.properties` with a PostgreSQL JDBC URL.

Example:
properties db.url=jdbc:postgresql://localhost:5432/comic_intake db.user=<DATABASE_USER> db.password=<DATABASE_PASSWORD>

> Do not commit real credentials to source control. Use placeholders, environment-specific config, or local-only property files when possible.

---

## Camera Integration

Camera support is provided through JavaCV/OpenCV.

The Maven dependency is:
xml org.bytedeco javacv-platform ${javacv.version}

The `javacv-platform` artifact includes native libraries for multiple platforms. This is convenient during development, though it can produce a larger packaged JAR.

For Windows-only distribution, the project notes that a platform-specific artifact may reduce the final size.

---

## Java Module Notes

The project includes a Java module descriptor:
src/main/java/module-info.java

The module declares JavaFX, UI utility, SQL, Gson, and OpenCV-related requirements.

Because JavaCV dependencies can involve automatic modules or classpath-based behavior, running from IntelliJ may require additional VM options in some configurations.

Useful VM option:
--add-reads com.comicdatacapture.comicdatacapture=ALL-UNNAMED

This grants the application module read access to classpath dependencies when needed.

---

## Export Capabilities

The application is designed to export captured intake data for external processing.

Supported export formats include:

### CSV

Useful for:

- Spreadsheet import
- Lightweight data review
- Integration with simple downstream tools

### Excel `.xlsx`

Provided through Apache POI.

Useful for:

- Human-readable reports
- Structured spreadsheet delivery
- Sharing capture results with non-technical users

### PostgreSQL Handoff

The project includes PostgreSQL support for scenarios where captured data needs to be stored or transferred into a PostgreSQL-backed workflow.

---

## Build and Run

### Prerequisites

Install:

- Java 21
- Maven, or use the included Maven Wrapper
- A connected camera if testing camera capture
- PostgreSQL only if using external database mode

Verify Java:
java --version
 
Expected major version:
21

---

### Run with Maven Wrapper on Windows
bash mvnw.cmd clean javafx:run

---

### Run with Maven Wrapper on macOS/Linux
bash ./mvnw clean javafx:run

---

### Run Tests
bash mvnw.cmd test

Or on macOS/Linux:
bash ./mvnw test

---

### Build the Project
bash mvnw.cmd clean package

The packaged output is generated under:
target/

---

## Packaging

The project uses the Maven Shade Plugin to create a packaged executable JAR.

The shaded JAR entry point is:
com.comicdatacapture.comicdatacapture.app.Launcher

The shade configuration excludes signature metadata and `module-info.class` from bundled dependencies to reduce packaging conflicts.

Relevant packaging plugin:
org.apache.maven.plugins maven-shade-plugin

---

## Development Notes

