package ca.teyssedre.android.transmissioncontrol.task;


import android.os.AsyncTask;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import ca.teyssedre.android.transmissioncontrol.model.TransmissionProfile;
import ca.teyssedre.android.transmissioncontrol.model.request.ListArgRequest;
import ca.teyssedre.android.transmissioncontrol.model.request.TransmissionRequest;
import ca.teyssedre.android.transmissioncontrol.model.response.ListArgsResponse;
import ca.teyssedre.restclient.HttpClient;
import ca.teyssedre.restclient.HttpRequest;
import ca.teyssedre.restclient.HttpResponse;
import ca.teyssedre.restclient.NoSSLValidation;

public class ListItems extends AsyncTask<String, String, TransmissionRequest<ListArgsResponse>> {

    private static final String TAG = "ListItems";
    HttpClient client;
    TransmissionProfile profile;
    OnTaskComplete<TransmissionRequest<ListArgsResponse>> callback;

    public ListItems(TransmissionProfile profile, OnTaskComplete<TransmissionRequest<ListArgsResponse>> callback) {
        this.callback = callback;

        this.client = new HttpClient();
        this.profile = profile;

        if (this.profile.isIgnoreSSL()) {
            client.ignoreCertificateValidation(true);
        }
    }

    @Override
    protected TransmissionRequest<ListArgsResponse> doInBackground(String... strings) {

        TransmissionRequest<ListArgRequest> torrents = new TransmissionRequest<>("torrent-get", new ListArgRequest());

        torrents.applyProfile(profile);

        HttpRequest request = torrents.getRequest();
        try {
            request.setSslFactory(new NoSSLValidation());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return executeRequest(request);
    }

    private TransmissionRequest<ListArgsResponse> executeRequest(HttpRequest request) {

        HttpResponse response = client.execute(request);

        int statusCode = response.getStatusCode();

        switch (statusCode) {
            case 200:
                return parseAnswer(response);
            case 409:
                Log.d(TAG, "Getting session id");
                String headerField = request.getConnection().getHeaderField("X-Transmission-Session-Id");
                if(headerField == null || headerField.length() == 0){
                    return null;
                }
                profile.setSessionId(headerField);
                request.addHeader("X-Transmission-Session-Id", headerField);
                return executeRequest(request);
            default:
                Log.d(TAG, "Request status:" + statusCode + " for " + request.getUrl());
                return null;
        }

    }

    private TransmissionRequest<ListArgsResponse> parseAnswer(HttpResponse response) {
        String data = response.getStringResponse();
        Log.d(TAG, "answer " + data);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        TransmissionRequest<ListArgsResponse> parsed = null;
        try {

            parsed = mapper.readValue(data, new TypeReference<TransmissionRequest<ListArgsResponse>>() {
            });

        } catch (IOException e) {
            e.printStackTrace();
            cancel(true);
        }
        return parsed;
    }

    @Override
    protected void onPostExecute(TransmissionRequest<ListArgsResponse> result) {
        if (callback != null) {
            if (result != null) {
                callback.onCompleted(result);
            } else {
                callback.onError();
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
//        if (callback != null) {
//            callback.onCancelled();
//        }
    }
}
