## Why this module
This module will be modified so that it can be delivered alone as a Maven artifact. It is intended as part of a backend and does not implement a user interface. It provides a REST API for communication with a user interface.

### What it will be able to do
- **User administration**: A tenant is linked to a user. Users can have different roles.
- **Multi-tenancy**: Multi-tenancy is an architecture in which a single instance of a software application serves several customers. Each customer is referred to as a client.
- **Sharing Data**: Certain information classes can be shared with all clients. The creator of an entity has the rights to make changes to this entity. Due to the different user roles, users with higher privileges can change this entity. 
- **Data change request**: A user with the least rights can indirectly make changes to an entity. To do this, the user changes the corresponding entity and thus causes a change proposal. This proposal can be accepted or rejected by the owner of the entity.
- **Message System**: Users sometimes need to communicate with a user who has more rights and functionality over the data. Occasionally an administrator also needs to send a message to the user of the application. Therefore there is an internal message system. 
- **Connector-Api-Key**: Sometimes external data sources require an API key. This can be managed here, whereby a fine adjustment is also possible for each data provider.
- **Batch processing monitor**: This library is a client-server application and therefore designed for 24/7 operation. This enables the sequential execution of various background tasks. The Rest API can be used to successfully monitor the execution of such a background task. Jobs can also be created or deleted manually using the REST API.
- **User defined fields**: Sometimes it is desirable for a user to be able to add additional properties to an information class. For example, the default properties for a security are not sufficient. Perhaps the security should also have an HTTP link to a website with a balance sheet. 