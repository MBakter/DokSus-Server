# DokSus OKIRU Server Documentation

## Purpose
This is the backend REST API for the DokSus restoration documentation system. It manages the creation and storage of conservation-restoration documentation, as well as research, seminar, and diploma theses at the Department of Conservation and Restoration of Works of Art at the Academy of Fine Arts in Zagreb.

## Technology
* **Language:** Kotlin (JDK 21)
* **Framework:** Spring Boot (Spring Web, Spring Security, Spring Data MongoDB)
* **Database:** MongoDB
* **Build Tool:** Gradle

## Prerequisites
* **Java Development Kit (JDK):** Version 21 or higher.
* **Database:** MongoDB server installed and running locally on port `27017` (default).

## Recommended IDE
**IntelliJ IDEA** (Ultimate or Community Edition). The project utilizes standard Spring Boot architecture and Gradle, which IntelliJ indexes automatically.

## Running the Project
  1. Open the project folder in IntelliJ IDEA.
  2. Allow Maven to download dependencies automatically.
  3. Run the Application.java main class directly from the IDE, or execute the following command in the integrated terminal:`./mvnw spring-boot:run`
