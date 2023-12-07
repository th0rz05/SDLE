package sdle.client.utils;

import com.google.gson.Gson;

public class Message {
    private String method;
    private String virtualnode;
    private String listUUID;
    private String listname;
    private String listcontent;
    private String serverId;
    private String hashRing;
    private String replicationLevel;
    private String nrVirtualNodes;
    private String keys;

    private String hintedHandoff;

    // Constructor
    public Message() {
        this.method = null;
        this.virtualnode = null;
        this.listUUID = null;
        this.listname = null;
        this.listcontent = null;
        this.serverId = null;
        this.hashRing = null;
        this.replicationLevel = null;
        this.nrVirtualNodes = null;
        this.keys = null;
        this.hintedHandoff = null;
    }

    // Getters and Setters (optional)
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getVirtualnode() {
        return virtualnode;
    }

    public void setVirtualnode(String virtualnode) {
        this.virtualnode = virtualnode;
    }

    public String getListUUID() {
        return listUUID;
    }

    public void setListUUID(String listUUID) {
        this.listUUID = listUUID;
    }

    public String getListname() {
        return listname;
    }

    public void setListname(String listname) {
        this.listname = listname;
    }

    public String getListcontent() {
        return listcontent;
    }

    public void setListcontent(String listcontent) {
        this.listcontent = listcontent;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getHashRing() {
        return hashRing;
    }

    public void setHashRing(String hashRing) {
        this.hashRing = hashRing;
    }

    public String getReplicationLevel() {
        return replicationLevel;
    }

    public void setReplicationLevel(String replicationLevel) {
        this.replicationLevel = replicationLevel;
    }

    public String getNrVirtualNodes() {
        return nrVirtualNodes;
    }

    public void setNrVirtualNodes(String nrVirtualNodes) {
        this.nrVirtualNodes = nrVirtualNodes;
    }

    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
    }

    public String getHintedHandoff() {
        return hintedHandoff;
    }

    public void setHintedHandoff(String hintedHandoff) {
        this.hintedHandoff = hintedHandoff;
    }

    // Serialize to JSON using Gson
    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    // Deserialize from JSON using Gson (if needed)
    public static Message fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, Message.class);
    }

    // toString method (optional for display purposes)
    @Override
    public String toString() {
        return "Message{" +
                "method='" + method + '\'' +
                ", virtualnode='" + virtualnode + '\'' +
                ", listUUID='" + listUUID + '\'' +
                ", listname='" + listname + '\'' +
                ", listcontent='" + listcontent + '\'' +
                ", serverId='" + serverId + '\'' +
                ", hashRing=" + hashRing +
                ", replicationLevel='" + replicationLevel + '\'' +
                ", nrVirtualNodes='" + nrVirtualNodes + '\'' +
                ", keys='" + keys + '\'' +
                ", hintedHandoff='" + hintedHandoff + '\'' +
                '}';
    }
}

