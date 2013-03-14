package org.neo4j.example.unmanagedextension;


import org.neo4j.graphdb.Relationship;

public class ConnectedResult {
    public Boolean isConnected;
    public Relationship connectedRelationship;

    public ConnectedResult(Boolean isConnected, Relationship connectedRelationship) {
        this.isConnected = isConnected;
        this.connectedRelationship = connectedRelationship;
    }

}
