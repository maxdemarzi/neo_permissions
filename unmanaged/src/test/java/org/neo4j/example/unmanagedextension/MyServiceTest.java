package org.neo4j.example.unmanagedextension;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.test.ImpermanentGraphDatabase;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MyServiceTest {

    private ImpermanentGraphDatabase db;
    private MyService service;
    private ObjectMapper objectMapper = new ObjectMapper();
    private static final RelationshipType KNOWS = DynamicRelationshipType.withName("KNOWS");
    private static final RelationshipType SECURITY = DynamicRelationshipType.withName("SECURITY");
    private static final RelationshipType IS_MEMBER_OF = DynamicRelationshipType.withName("IS_MEMBER_OF");
    private static final RelationshipType HAS_CHILD_CONTENT = DynamicRelationshipType.withName("HAS_CHILD_CONTENT");

    @Before
    public void setUp() {
        db = new ImpermanentGraphDatabase();
        populateDb(db);
        service = new MyService();
    }

    private void populateDb(GraphDatabaseService db) {
        Transaction tx = db.beginTx();
        try
        {
            Node personA = createPerson(db, "A");
            Node personB = createPerson(db, "B");
            Node personC = createPerson(db, "C");
            Node personD = createPerson(db, "D");
            personA.createRelationshipTo(personB, KNOWS);
            personB.createRelationshipTo(personC, KNOWS);
            personC.createRelationshipTo(personD, KNOWS);
            Node doc1 = createDocument(db, "DOC1");
            Node doc2 = createDocument(db, "DOC2");
            Node doc3 = createDocument(db, "DOC3");
            Relationship sec1 = createPermission(db, personA, doc1, "true", "true");
            Relationship sec2 = createPermission(db, personA, doc3, "true", "true");
            Node g1 = createGroup(db, "G1");
            personA.createRelationshipTo(g1, IS_MEMBER_OF);
            Relationship sec3 = createPermission(db, g1, doc2, "true", "true");

            Node doc4 = createDocument(db, "DOC4");
            doc1.createRelationshipTo(doc4, HAS_CHILD_CONTENT);
            Node doc5 = createDocument(db, "DOC5");
            doc2.createRelationshipTo(doc5, HAS_CHILD_CONTENT);
            Node doc6 = createDocument(db, "DOC6");
            Node doc7 = createDocument(db, "DOC7");
            doc5.createRelationshipTo(doc7, HAS_CHILD_CONTENT);
            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    private Node createPerson(GraphDatabaseService db, String name) {
        Index<Node> people = db.index().forNodes("people");
        Node node = db.createNode();
        node.setProperty("name", name);
        people.add(node, "name", name);
        return node;
    }

    private Node createDocument(GraphDatabaseService db, String uid) {
        Index<Node> documents = db.index().forNodes("Documents");
        Node node = db.createNode();
        node.setProperty("Uid", uid);
        documents.add(node, "Uid", uid);
        return node;
    }

    private Relationship createPermission(GraphDatabaseService db, Node person, Node doc, String see, String read) {
        Relationship sec = person.createRelationshipTo(doc,SECURITY);
        sec.setProperty("See", see);
        sec.setProperty("Read", read);
        return sec;
    }

    private Node createGroup(GraphDatabaseService db, String uid) {
        Node node = db.createNode();
        node.setProperty("Uid", uid);
        node.setProperty("Type", "SecurityGroup");
        return node;
    }

    @After
    public void tearDown() throws Exception {
        db.shutdown();

    }

    @Test
    public void shouldRespondToHelloWorld() {
        assertEquals("Hello World!", service.helloWorld());
    }

    @Test
    public void shouldQueryDbForFriends() throws IOException {
        Response response = service.getFriends("B", db);
        List list = objectMapper.readValue((String) response.getEntity(), List.class);
        assertEquals(new HashSet<String>(Arrays.asList("A", "C")), new HashSet<String>(list));
    }

    public GraphDatabaseService graphdb() {
        return db;
    }
}
