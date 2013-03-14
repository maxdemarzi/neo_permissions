package org.neo4j.example.unmanagedextension;

import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.IndexHits;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

@Path("/service")
public class MyService {

    @GET
    @Path("/helloworld")
    public String helloWorld() {
        return "Hello World!";
    }

    @GET
    @Path("/friends/{name}")
    public Response getFriends(@PathParam("name") String name, @Context GraphDatabaseService db) throws IOException {
        ExecutionEngine executionEngine = new ExecutionEngine(db);
        ExecutionResult result = executionEngine.execute("START person=node:Users(Uid={n}) MATCH person-[:KNOWS]-other RETURN other.Uid",
                Collections.<String, Object>singletonMap("n", name));
        List<String> friends = new ArrayList<String>();
        for (Map<String, Object> item : result) {
            friends.add((String) item.get("other.Uid"));
        }
        return Response.ok().entity(objectMapper.writeValueAsString(friends)).build();
    }

    private ObjectMapper objectMapper = new ObjectMapper();
    private static enum RelTypes implements RelationshipType
    {
        IS_MEMBER_OF,
        SECURITY,
        HAS_CHILD_CONTENT
    }


    @POST
    @Path("/permissions")
    public Response permissions(String body, @Context GraphDatabaseService db) throws IOException {
        String[] splits = body.split(",");
        PermissionRequest ids = new PermissionRequest(splits[0], splits[1]);
        Set<String> documents = new HashSet<String>();
        Set<Node> documentNodes = new HashSet<Node>();
        List<Node> groupNodes = new ArrayList<Node>();
        Set<Node> parentNodes = new HashSet<Node>();
        HashMap<Node, ArrayList<Node>> foldersAndDocuments = new HashMap<Node, ArrayList<Node>>();

        IndexHits<Node> uid = db.index().forNodes("Users").get("Uid", ids.userAccountUid);
        IndexHits<Node> docids = db.index().forNodes("Documents").query("Uid:(" + ids.documentUids + ")");
        try
        {
            for ( Node node : docids )
            {
                documentNodes.add(node);
            }
        }
        finally
        {
            docids.close();
        }

        if ( uid.size() > 0 && documentNodes.size() > 0)
        {
            Node user = uid.getSingle();
            for ( Relationship relationship : user.getRelationships(
                    RelTypes.IS_MEMBER_OF, Direction.OUTGOING ) )
            {
                groupNodes.add(relationship.getEndNode());
            }

            Iterator listIterator ;
            do {
                listIterator = documentNodes.iterator();
                Node document = (Node) listIterator.next();
                listIterator.remove();

                //Check against user
                Node found = getAllowed(document, user);
                if (found != null) {
                    if (foldersAndDocuments.get(found) != null) {
                        for(Node docs : foldersAndDocuments.get(found)) {
                            documents.add(docs.getProperty("Uid").toString());
                        }
                    } else {
                        documents.add(found.getProperty("Uid").toString());
                    }
                }

                //Check against user Groups
                for (Node group : groupNodes){
                    found = getAllowed(document, group);
                    if (found != null) {
                        if (foldersAndDocuments.get(found) != null) {
                            for(Node docs : foldersAndDocuments.get(found)) {
                                documents.add(docs.getProperty("Uid").toString());
                            }
                        } else {
                            documents.add(found.getProperty("Uid").toString());
                        }
                    }
                }
                // Did not find a security relationship, go up the folder chain
                Relationship parentRelationship = document.getSingleRelationship(RelTypes.HAS_CHILD_CONTENT,Direction.INCOMING);
                if (parentRelationship != null){
                    Node parent = parentRelationship.getStartNode();
                    ArrayList<Node> myDocs = foldersAndDocuments.get(document);
                    if(myDocs == null) myDocs = new ArrayList<Node>();

                    ArrayList<Node> existingDocs = foldersAndDocuments.get(parent);
                    if(existingDocs == null) existingDocs = new ArrayList<Node>();

                    for (Node myDoc:myDocs) {
                        existingDocs.add(myDoc);
                    }
                    if (myDocs.isEmpty()) existingDocs.add(document);
                    foldersAndDocuments.put(parent, existingDocs);
                    parentNodes.add(parent);
                }
                if(listIterator.hasNext() == false){
                    documentNodes.clear();

                    for( Node parentNode : parentNodes){
                        documentNodes.add(parentNode);
                    }

                    parentNodes.clear();
                    listIterator = documentNodes.iterator();
                }

            } while (listIterator.hasNext());


        } else {documents.add("Error: User or Documents not found");}

        uid.close();

        return Response.ok().entity(objectMapper.writeValueAsString(documents)).build();
    }

    private Node getAllowed(Node from, Node to){
        ConnectedResult connectedResult = isConnected(from, to, Direction.INCOMING, RelTypes.SECURITY);
        if (connectedResult.isConnected){
            if (connectedResult.connectedRelationship.getProperty("flags").toString().contains("R")) {
                return connectedResult.connectedRelationship.getEndNode();
            }
        }
        return null;
    }

    private ConnectedResult isConnected(Node from, Node to, Direction dir, RelationshipType type) {
        for (Relationship r : from.getRelationships(dir, type)) {
            if (r.getOtherNode(from).equals(to)) return new ConnectedResult(true, r);
        }
        return new ConnectedResult(false, null);
    }

}
