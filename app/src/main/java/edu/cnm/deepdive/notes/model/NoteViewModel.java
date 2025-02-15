package edu.cnm.deepdive.notes.model;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import dagger.hilt.android.lifecycle.HiltViewModel;
import edu.cnm.deepdive.notes.model.entity.Note;
import edu.cnm.deepdive.notes.service.NoteRepository;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import javax.inject.Inject;
import kotlin.random.Random;
import kotlin.random.Random.Default;

@HiltViewModel
public class NoteViewModel extends ViewModel implements DefaultLifecycleObserver {

  private final NoteRepository noteRepository;
  private final MutableLiveData<Long> noteId;
  private final LiveData<Note> note;
  private final MutableLiveData<Throwable> throwable;
  //for pending asynchronous jobs
  private final CompositeDisposable pending;

  //Constructor
  @Inject
  public NoteViewModel(NoteRepository noteRepository) {
    this.noteRepository = noteRepository;
    noteId = new MutableLiveData<>();
    note = Transformations.switchMap(noteId, noteRepository::get); //triggers a query & returns live data, the repository passes that along
    //the object we're triggering is noteRepository with id
    throwable = new MutableLiveData<>();
    pending = new CompositeDisposable();
  }

  public void save(Note note) {
    // DONE: 2/12/25  Reset our throwable livedata.
    throwable.setValue(null);  // Will be invoked from controller on UI thread
    noteRepository //invoking save by the UI, which gets a machine which will save it-- Single of note-- Single<Note>
        .save(note)  //no semicolon after note in order to invoke the next method, chain method invocations
        .ignoreElement()
        .subscribe(
            //here we say how we consume what comes out, so we set our consumer for a successful result
            () -> {},
            //this is a subscription that has a consumer ONLY for successful result
            this::postThrowable,
            //invoking postThrowable on "this" instance of this class, reactiveX catches exception and passes it to log
            pending
        );
      // DONE: 2/12/25 Invoke the save method of repository to get the machine for saving.
    // DONE: 2/12/25 Invoke the subscribe method on the machine, to start it and attach consumers.
    // DONE: 2/12/25 there will be 2 consumers, one for the successful result (a Note) and one for
    //  an unsuccessful result (a Throwable). THe first puts the Note into a LIveData bucket, the
    //  second invokes helper method.
  }

  public void fetch(long noteId) { //will fetch an id
    throwable.setValue(null);
    this.noteId.setValue(noteId);
  }
//get invokes action, triggering a chain of things happening

  public void delete(Note note) {
    throwable.setValue(null);
    noteRepository
        .remove(note) //returns a Completable, to turn on the machinery, must subscribe
        .subscribe(
            () -> {},
            this::postThrowable,
            pending
        );
  }

  public MutableLiveData<Long> getNoteId() {
    return noteId;
  }

  public LiveData<Note> getNote() {
    return note;
  }

  public LiveData<List<Note>> getNotes() { //when UI invokes this, it gets live data, when it observes it, query happens
    return noteRepository.getAll();
  }

  public MutableLiveData<Throwable> getThrowable() {
    return throwable;
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    pending.clear();
    DefaultLifecycleObserver.super.onStop(owner);
  }

  private void postThrowable(Throwable throwable) {
    Log.e(NoteViewModel.class.getSimpleName(), throwable.getMessage(), throwable);
    this.throwable.postValue(throwable); //this is a consumer of throwable, ONLY for unsuccessful results
  }
}