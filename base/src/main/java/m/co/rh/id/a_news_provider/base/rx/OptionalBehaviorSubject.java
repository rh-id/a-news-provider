package m.co.rh.id.a_news_provider.base.rx;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Optional;

import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class OptionalBehaviorSubject<E extends Serializable> implements Serializable {
    private transient BehaviorSubject<Optional<E>> mSubject;

    public OptionalBehaviorSubject() {
        mSubject = BehaviorSubject.createDefault(Optional.empty());
    }

    public OptionalBehaviorSubject(E element) {
        mSubject = BehaviorSubject.createDefault(Optional.ofNullable(element));
    }

    public BehaviorSubject<Optional<E>> getSubject() {
        return mSubject;
    }

    public Optional<E> getValue() {
        return mSubject.getValue();
    }

    public void onNext(E element) {
        mSubject.onNext(Optional.ofNullable(element));
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(mSubject.getValue().orElse(null));
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        E element = (E) in.readObject();
        mSubject = BehaviorSubject.createDefault(Optional.ofNullable(element));
    }
}
