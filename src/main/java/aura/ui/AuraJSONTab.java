/*
 * Copyright (c) 2020, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package aura.ui;

import java.awt.Component;
import java.io.IOException;

import burp.*;
import com.codemagi.burp.BaseExtender;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AuraJSONTab implements IMessageEditorTab {

    private static final String AURA_INDICATOR = "aura.token";
    private static final String AURA_RESPONSE_START = "while(1)";
    private static final String AURA_DATAPARAM = "message";
	private static final String TAB_CAPTION = "Aura JSON";
	private String auraDataparam;
	private String caption;

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private IHttpService httpService;
    public ITextEditor jsonText;
    public boolean editable;
    public byte[] content;

    public AuraJSONTab(IMessageEditorController controller, boolean editable) {
        this.callbacks = BurpExtender.getCallbacks();
        this.helpers = callbacks.getHelpers();
        this.editable = editable;
        this.httpService = controller.getHttpService();
        this.jsonText = callbacks.createTextEditor();
        jsonText.setEditable(editable);
		this.auraDataparam = AURA_DATAPARAM;
		this.caption = TAB_CAPTION;
    }

    public AuraJSONTab(IMessageEditorController controller, boolean editable, String auraDataparam, String caption) {
        this.callbacks = BurpExtender.getCallbacks();;
        this.helpers = callbacks.getHelpers();
        this.editable = editable;
        this.httpService = controller.getHttpService();
        this.jsonText = callbacks.createTextEditor();
        jsonText.setEditable(editable);
		this.auraDataparam = auraDataparam;
		this.caption = caption;
    }

    @Override
    public String getTabCaption() {
        return caption;
    }

    @Override
    public Component getUiComponent() {
        return jsonText.getComponent();
    }

    @Override
    public boolean isEnabled(byte[] content, boolean isRequest) {
        if (content == null || content.length == 0)
            return false;
        // No support for responses yet (invalid JSON)
        return (isRequest && isRequestEnabled(content));// || isResponseEnabled(content);			
    }

    private boolean isRequestEnabled(byte[] content) {
        if (content == null || content.length == 0)
            return false;

        boolean isAuraMessage = (null != helpers.getRequestParameter(content, AURA_INDICATOR));

        if (this.httpService != null) {
            boolean isAuraEndpoint = true; // true until proven wrong
            URL requestUrl = Utils.getRequestUrl(httpService, content);

            if (requestUrl != null) {
                isAuraEndpoint = requestUrl.getPath().contains("/aura");
            }
            return isAuraEndpoint && isAuraMessage;
        } else {
            return isAuraMessage;
        }
    }

    //TODO figure out general JSON for response
    private boolean isResponseEnabled(byte[] content) {
        IResponseInfo response = helpers.analyzeResponse(content);
        String mimeType = response.getStatedMimeType();
        if (!mimeType.equals("JSON")) {
            return false;
        }
        String body = helpers.bytesToString(content);
        body = body.substring(response.getBodyOffset());
        return body.substring(0, AURA_RESPONSE_START.length()).equals(AURA_RESPONSE_START);
    }

    @Override
    public void setMessage(byte[] content, boolean isRequest) {
        if (content == null || content.length == 0)
            return;
        this.content = content;
        this.jsonText.setText("".getBytes(StandardCharsets.UTF_8));
        IParameter param = helpers.getRequestParameter(content, auraDataparam);

        String jsonString = param.getValue();
        try {
            jsonString = Utils.urlDecode(jsonString);
            String pretty = getPrettifiedJSON(jsonString);
            this.jsonText.setText(pretty.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            BaseExtender.printStackTrace(e);
            this.jsonText.setText(jsonString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            BaseExtender.printStackTrace(e);
            this.jsonText.setText(jsonString.getBytes(StandardCharsets.UTF_8));
        }

    }

    @Override
    public byte[] getMessage() {
        if (!isModified()) {
            return this.content;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode auraActionsJson = null;

        String prettyJsonString = new String(this.jsonText.getText(), StandardCharsets.UTF_8);
        try {
            auraActionsJson = mapper.readValue(prettyJsonString, JsonNode.class);
        } catch (JsonParseException e) {
            callbacks.issueAlert("Invalid JSON entered, using original payload");
            BaseExtender.printStackTrace(e);
            return this.content;
        } catch (JsonMappingException e) {
            callbacks.issueAlert("Invalid JSON entered, using original payload");
            BaseExtender.printStackTrace(e);
            return this.content;
        } catch (IOException e) {
            callbacks.issueAlert("IOException in " + caption + " tab");
            BaseExtender.printStackTrace(e);
            return this.content;
        }

        String auraActionsString = Utils.urlEncode(auraActionsJson.toString());
        IParameter messageParam = helpers.buildParameter(auraDataparam, auraActionsString, IParameter.PARAM_BODY);
        return helpers.updateParameter(content, messageParam);
    }

    @Override
    public boolean isModified() {
        return this.jsonText.isTextModified();
    }

    @Override
    public byte[] getSelectedData() {
        return this.jsonText.getSelectedText();
    }

    private String getPrettifiedJSON(String inputText) throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(inputText);
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        return pretty;
    }

}
