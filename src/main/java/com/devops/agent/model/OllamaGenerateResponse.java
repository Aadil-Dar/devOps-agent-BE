package com.devops.agent.model;

public class OllamaGenerateResponse {

    private String model;
    private String created_at;
    private String response;  // the generated text
    // There are other fields like "done", "total_duration" etc. which we can ignore for now.

    public OllamaGenerateResponse() {
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
