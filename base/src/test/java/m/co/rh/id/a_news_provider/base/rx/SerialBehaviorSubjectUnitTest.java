package m.co.rh.id.a_news_provider.base.rx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SerialBehaviorSubjectUnitTest {
    @SuppressWarnings("unchecked")
    @Test
    public void serialize() throws IOException, ClassNotFoundException {
        String test = "this is a test";
        SerialBehaviorSubject<String> stringSerialBehaviorSubject = new SerialBehaviorSubject<>(test);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos);
        objectOutputStream.writeObject(stringSerialBehaviorSubject);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(bis);
        SerialBehaviorSubject<String> serializedValue = (SerialBehaviorSubject<String>) objectInputStream.readObject();

        assertEquals(stringSerialBehaviorSubject.getValue(), serializedValue.getValue());
    }
}