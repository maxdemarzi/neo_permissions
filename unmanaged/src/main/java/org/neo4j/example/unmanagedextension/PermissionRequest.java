package org.neo4j.example.unmanagedextension;

public class PermissionRequest {
    public String userAccountUid;
    public String documentUids;

    public PermissionRequest(String userAccountUid, String documentUids) {
        this.userAccountUid = userAccountUid;
        this.documentUids = documentUids;
    }

}
