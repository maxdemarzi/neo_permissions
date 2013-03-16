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
            Node doc1 = createDocument(db, "DOC1");
            Node doc2 = createDocument(db, "DOC2");
            Node doc3 = createDocument(db, "DOC3");
            Node doc4 = createDocument(db, "DOC4");
            Node doc5 = createDocument(db, "DOC5");
            Node doc6 = createDocument(db, "DOC6");
            Node doc7 = createDocument(db, "DOC7");
            Node g1 = createGroup(db, "G1");
            Node g2 = createGroup(db, "G2");

            personA.createRelationshipTo(personB, KNOWS);
            personB.createRelationshipTo(personC, KNOWS);
            personC.createRelationshipTo(personD, KNOWS);

            personA.createRelationshipTo(g1, IS_MEMBER_OF);
            personB.createRelationshipTo(g2, IS_MEMBER_OF);

            doc1.createRelationshipTo(doc4, HAS_CHILD_CONTENT);
            doc2.createRelationshipTo(doc5, HAS_CHILD_CONTENT);
            doc5.createRelationshipTo(doc7, HAS_CHILD_CONTENT);

            Relationship secA1 = createPermission(personA, doc1, "R");
            Relationship secA3 = createPermission(personA, doc3, "RW");
            Relationship secB4 = createPermission(personB, doc4, "R");
            Relationship secG2 = createPermission(g1, doc2, "R");
            Relationship secG6 = createPermission(g2, doc6, "R");

            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    private Node createPerson(GraphDatabaseService db, String uid) {
        Index<Node> people = db.index().forNodes("Users");
        Node node = db.createNode();
        node.setProperty("unique_id", uid);
        people.add(node, "unique_id", uid);
        return node;
    }

    private Node createDocument(GraphDatabaseService db, String uid) {
        Index<Node> documents = db.index().forNodes("Documents");
        Node node = db.createNode();
        node.setProperty("unique_id", uid);
        documents.add(node, "unique_id", uid);
        return node;
    }

    private Relationship createPermission(Node person, Node doc, String permission) {
        Relationship sec = person.createRelationshipTo(doc, SECURITY);
        sec.setProperty("flags", permission);
        return sec;
    }

    private Node createGroup(GraphDatabaseService db, String uid) {
        Node node = db.createNode();
        node.setProperty("unique_id", uid);
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

    @Test
    public void shouldRespondToPermissions() throws BadInputException, IOException {
        String ids = "A,DOC1 DOC2 DOC3 DOC4 DOC5 DOC6 DOC7";
        Response response =  service.permissions(ids, db);
        List list = objectMapper.readValue((String) response.getEntity(), List.class);
        assertEquals(new HashSet<String>(Arrays.asList("DOC1", "DOC2", "DOC3", "DOC4", "DOC5", "DOC7")), new HashSet<String>(list));
    }

    @Test
    public void shouldRespondToPermissions2() throws BadInputException, IOException {
        String ids = "B,DOC1 DOC2 DOC3 DOC4 DOC5 DOC6 DOC7";
        Response response =  service.permissions(ids, db);
        List list = objectMapper.readValue((String) response.getEntity(), List.class);
        assertEquals(new HashSet<String>(Arrays.asList("DOC4", "DOC6")), new HashSet<String>(list));
    }

    public GraphDatabaseService graphdb() {
        return db;
    }
}
