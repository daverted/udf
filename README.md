# Jira Integration UDF

This function syncs OverOps events with Jira issues.

## Table of Contents

[Getting Started](#getting-started)  
[Building](#building)  
[Testing](#testing)  

## Getting Started

TODO

## Building

```console
./gradlew clean :jira-integration:fatJar
```

## Testing

```console
cd ./jira-integration/build/libs

jar xf jira-integration-*.jar

java -cp . com.overops.udf.jira.JiraIntegrationFunction $API_URL $API_KEY $ENV_ID $JIRA_URL $JIRA_USERNAME $JIRA_PASSWORD
```