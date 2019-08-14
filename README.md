# VMAMV
The VMAMV is a tool for monitoring microservice systems, generating visualized version-based service dependency graphs, and providing graph search
services. The proposed scheme is called Version-based Microservice Analysis, Monitoring, and Visualization (VMAMV). This system automatically detects potential design
problems and service anomalies and immediately notifies users of problems before or shortly after they occur.
# VMAMVS (vmamv-service)
The VMAMV system consists of [VMAMVS (vmamv-service)](https://github.com/Joe831216/vmamv-service) and client-side libraries. The VMAMVS is the major service of VMAMV system.
## How to use?
### Dependencies
VMAMVS dependents on [Neo4j](https://neo4j.com/) + [APOC](https://neo4j-contrib.github.io/neo4j-apoc-procedures/) and [Elasticsearch](https://www.elastic.co), make sure you already deploy them.
* Neo4j Recommend version: 3.5
* Elasticsearch Recommend version: 6.7
### Configurations
Configure the following properties:


`server.port`  
`spring.data.neo4j.username`  
`spring.data.neo4j.password`  
`spring.data.neo4j.uri`  
`spring.data.elasticsearch.cluster-name`  
`spring.data.elasticsearch.cluster-nodes`  
`management.endpoints.web.exposure.include=*`  
### Operations
You can access the monitoring dashboard by visiting the root path of VMAMVS.
