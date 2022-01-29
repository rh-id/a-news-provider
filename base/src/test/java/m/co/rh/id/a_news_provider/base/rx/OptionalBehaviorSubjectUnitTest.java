package m.co.rh.id.a_news_provider.base.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class OptionalBehaviorSubjectUnitTest {
    @SuppressWarnings("unchecked")
    @Test
    public void serialize() throws IOException, ClassNotFoundException {
        String test = "this is a test";
        OptionalBehaviorSubject<String> stringSerialBehaviorSubject = new OptionalBehaviorSubject<>(test);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos);
        objectOutputStream.writeObject(stringSerialBehaviorSubject);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(bis);
        OptionalBehaviorSubject<String> serializedValue = (OptionalBehaviorSubject<String>) objectInputStream.readObject();

        assertEquals(stringSerialBehaviorSubject.getValue().get(), serializedValue.getValue().get());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serialize_empty() throws IOException, ClassNotFoundException {
        OptionalBehaviorSubject<String> stringSerialBehaviorSubject = new OptionalBehaviorSubject<>();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos);
        objectOutputStream.writeObject(stringSerialBehaviorSubject);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(bis);
        OptionalBehaviorSubject<String> serializedValue = (OptionalBehaviorSubject<String>) objectInputStream.readObject();

        assertFalse(serializedValue.getValue().isPresent());
    }
}