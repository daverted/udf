package com.example.udf.helloworld;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.request.event.EventRequest;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.core.url.UrlClient.Response;

public class HelloWorld {

  public static void main(String[] args) {

    EventRequest eventRequest = EventRequest.newBuilder()
      .setServiceId("S37295")
      .setEventId("119")
      .build();

    ApiClient apiClient = ApiClient.newBuilder()
      .setHostname("https://api.overops.com")
      .setApiKey("fP5cAd73HOSpn8b5VJTznj5MUrxPXFW3jwzJq2++")
      .build();

    // GET event data
    Response<EventResult> eventResponse = apiClient.get(eventRequest);

    // check for a bad API response (HTTP status code >= 300)
    if (eventResponse.isBadResponse())
      throw new IllegalStateException("Failed getting events.");

    // EventResult is a POJO: /blob/master/api-client/src/main/java/com/takipi/api/client/result/event/EventResult.java
    EventResult eventResult = eventResponse.data;
    
    System.out.println("ID: " + eventResult.id);
    System.out.println("Introduced by: " + eventResult.introduced_by);
    System.out.println("Name " + eventResult.name);
    System.out.println("Message: " + eventResult.message);
    
  }
}
