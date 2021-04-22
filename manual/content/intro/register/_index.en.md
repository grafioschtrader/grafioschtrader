---
title: "Register"
date: 2021-03-23T11:00:00+01:00
draft: false
weight : 30
chapter: true
---
## Register
For registration, the user must fill out two forms. The first for his data and the second for the client's data. In between he has to confirm an email and log in to GT for the first time. The registration process ends with the login form.
{{< mermaid >}}
sequenceDiagram
    autonumber
    participant User
    participant A as Sign in form
    participant R as Register form
    participant K as Clinet form
    participant Backend
    User->>A: Select register
    A-->>R: Redirect
    User->>R: User registrieren
    R-->>Backend: Check input
    Backend-->>User: Email with confirmation URI  
    User->>Backend: Confirms URI
    Backend-->>User: Redirect Sign in form
    User->>A: User logs in
    A-->Backend: Check authentication
    Backend-->>User: Redirect Client form
    User->>K: Enter client
    K-->>Backend: Check input
    Backend-->>A: Redirect Sign in form
{{< /mermaid >}}
It looks easier in the video...
### Eigenschaften
- **Spitzname**: This nickname is unique per GT instance, otherwise you could not be distinguished from the other users. Currently, this is not yet used between users.
- **E-Mail**: Must be one unique to the GT instance, otherwise GT could not distinguish you from other users.