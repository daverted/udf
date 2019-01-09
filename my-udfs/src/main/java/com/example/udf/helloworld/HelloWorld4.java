package com.example.udf.helloworld;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.request.view.ViewsRequest;
import com.takipi.api.client.result.view.ViewsResult;
import com.takipi.api.core.url.UrlClient.Response;

public class HelloWorld4 {

	public static void main(String[] args) {

		String serviceId = "S37295";

		ApiClient apiClient = ApiClient.newBuilder().setHostname("https://api.overops.com")
				.setApiKey("fP5cAd73HOSpn8b5VJTznj5MUrxPXFW3jwzJq2++").build();

		// get all views
		ViewsRequest viewsRequest = ViewsRequest.newBuilder().setServiceId(serviceId).build();

		// GET view data
		Response<ViewsResult> viewsResponse = apiClient.get(viewsRequest);

		// check for a bad API response (HTTP status code >= 300)
		if (viewsResponse.isBadResponse())
			throw new IllegalStateException("Failed getting views.");

		ViewsResult viewsResult = viewsResponse.data;

	    for(SummarizedView view : viewsResult.views) {
	    	System.out.println(view.name + "(" + view.id + ")");
	    }

	}
}
