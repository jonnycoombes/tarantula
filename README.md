# Facade Redux

This is the revised and upgraded version of the CS16 REST facade, deployed and used by Discovery Bank in Johannesburg, amongst others. This
represents a significant upgrade to the original Facade built in 2017 and contains a number of performance, functionality and stability
improvements.

## Baseline Environment

The baseline build and deployment environment for this version of the facade is as follows:


| Component | Version |
| ----------- | --------- |
| Scala     | 2.13.6  |
| JDK       | 11.0.12 |
| SBT       | 1.5.5   |

## Dependencies

This version of the facade has built-in support for version 16.2.x of OpenText Content Server. This version of the facade will not be
actively tested and regressed against earlier versions, and will not offer direct support for versions > 16.  (I.e. Content Server 20).

The facade as built assumes an underlying OTCS schema hosted on SQL Server or SQL Services within Azure.

## Key Design Objectives

The key design objectives for the revised version of the facade are listed below:

* Improve overall performance for retrieval of stored documents such as bank statements, proof of identification etc...
* Minimise the required number of CWS outcalls needed in order to service each inbound Facade request.
* Reduce/amortise the number of OTDS authentication calls required within the Facade web services layer.
* Simplify the overall code base, spring clean out any unnecessary/convoluted junk in the code base.
* Remove complexity arising from the use of an actor-based request model.
* Simplfiication of the facade configuration - default to "by convention" approach rather than explicit configuration.
* Self scaffolding of any supporting SQL elements.

##Implementation Notes

## Configuration

The table below gives a breakdown of the key facade configuration parameters, which should be set within the *facade.conf* HCON file.  (By default, this file is located within the *conf* subdirectory under the application root.


| Configuration Parameter Key | Description                          | Example      | Default    |
| ----------------------------- | -------------------------------------- | -------------- | ------------ |
| facade.system.identifier    | The system identifier (user-defined) | "facade-dev" | "hyperion" |
|                             |                                      |              |            |
