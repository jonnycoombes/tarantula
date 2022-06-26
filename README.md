# __Facade Redux__

This is a simplified, Scala-based REST facade for OTCS 16.

## Version Information
The shipping versions for the revised facade are as follows:

| Version | Date | Notes |
|---------|------|-------|
| 2.0.0   |10/10/2021| Initial tested version |
| 2.0.1   | 15/10/2021 | To include Swagger/OpenAPI |


## __Build Environment__

The baseline build and deployment environment for this version of the facade is as follows:


| Component | Version |
| ----------- | --------- |
| Scala     | 2.13.6  |
| JDK       | 11.0.12 |
| SBT       | 1.5.5   |
| JRE       | 11.0.12


## __Supported Environments__

This version of the facade has built-in support for version 16.2.x of OpenText Content Server. This version of the facade will not be
actively tested and regressed against earlier versions, and will not offer direct support for versions > 16.  (I.e. Content Server 20).

The facade as built assumes an underlying OTCS schema hosted on SQL Server or SQL Services within Azure.

The facade may be hosted on any platform supporting the underlying JRE. (It is tested against Windows, Linux & OSX by default).


## __Design Objectives__

The key design objectives for the revised version of the facade are listed below:

* Improve overall performance for retrieval of stored documents such as bank statements, proof of identification etc...
* Minimise the required number of CWS outcalls needed in order to service each inbound Facade request.
* Reduce/amortise the number of OTDS authentication calls required within the Facade web services layer.
* Simplify the overall code base, spring clean out any unnecessary/convoluted junk in the code base.
* Remove complexity arising from the use of an actor-based request model.
* Simplfiication of the facade configuration - default to "by convention" approach rather than explicit configuration.
* Self scaffolding of any supporting SQL elements.


## __Implementation Notes__

### __Internal Changes to Previous Versions__


### __Facade Configuration__

The table below gives a breakdown of the key facade configuration parameters, which should be set within the *facade.conf* HCON file.  (By default, this file is located within the *conf* subdirectory under the application root.


| Configuration Parameter Key | Description                          | Example      | Default    |
| ----------------------------- | -------------------------------------- | -------------- | ------------ |
| facade.system.identifier    | The system identifier (user-defined) | "facade-dev" | "hyperion" |
|                             |                                      |              |            |

### __Functional Changes To Previous Versions__

As far as possible, this version of the Facade attempts to retain functional compatibility with previous versions.  However, in certain 
areas (largely for simplification reasons) a number of functional changes have been made.  These are detailed below.

#### __Ping returns more sensible information__
Within version 2.0 of the facade, the ping request has been enhanced so that it now returns a variety of information relating to the health of the underlying 
OTCS instance, database and facade itself.


#### __Metadata is always returned for nodes__
In previous versions of the facade, in order to retrieve the metadata (other than the core information) for a given object within the 
underlying repository, it was necessary to set the *meta* query string parameter to *true*. This parameter has been removed within this 
version, and full metadata is always returned. This change has been made given that the aggressive caching strategies employed within 
this version of the facade amortise any performance overhead associated with the translation of the native CWS meta-data structures and 
the required JSON outbound payload.  For any given node,  the JSON rendition of it's core information and associated metadata is 
*cached* wherever possible , so that successive calls for the same meta-data will incur minimal overhead.



