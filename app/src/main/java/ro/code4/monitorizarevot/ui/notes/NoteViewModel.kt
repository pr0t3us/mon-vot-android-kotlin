package ro.code4.monitorizarevot.ui.notes

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.koin.core.inject
import ro.code4.monitorizarevot.adapters.NoteAdapter.Companion.TYPE_NOTE
import ro.code4.monitorizarevot.adapters.NoteAdapter.Companion.TYPE_SECTION
import ro.code4.monitorizarevot.adapters.helper.ListItem
import ro.code4.monitorizarevot.data.model.Note
import ro.code4.monitorizarevot.data.model.Question
import ro.code4.monitorizarevot.helper.*
import ro.code4.monitorizarevot.repositories.Repository
import ro.code4.monitorizarevot.ui.base.BaseViewModel
import java.io.File


class NoteViewModel : BaseViewModel() {
    private val repository: Repository by inject()
    private val preferences: SharedPreferences by inject()
    private val app: Application by inject()
    private var countyCode: String
    private var branchNumber: Int = -1
    private val notesLiveData = MutableLiveData<ArrayList<ListItem>>()
    private val fileNameLiveData = MutableLiveData<String>()
    private val submitCompletedLiveData = SingleLiveEvent<Boolean>()
    private var noteFile: File? = null
    private val listObserver =
        Observer<List<Note>> { list ->
            processList(list)
        }


    companion object {
        @JvmStatic
        val TAG = NoteViewModel::class.java.simpleName
    }

    init {
        countyCode = preferences.getCountyCode()!!
        branchNumber = preferences.getBranchNumber()
    }

    fun notes(): LiveData<ArrayList<ListItem>> = notesLiveData
    fun fileName(): LiveData<String> = fileNameLiveData
    fun submitCompleted(): LiveData<Boolean> = submitCompletedLiveData
    private var selectedQuestion: Question? = null
    fun setData(question: Question?) {
        selectedQuestion = question
        repository.getNotes(countyCode, branchNumber, selectedQuestion).observeOnce(listObserver)
    }

    private fun processList(notes: List<Note>) {
        if (notes.isNotEmpty()) {
            val list = ArrayList<ListItem>(notes.size + 1)
            list.add(ListItem(TYPE_SECTION, ro.code4.monitorizarevot.R.string.notes_history))
            list.addAll(notes.map { ListItem(TYPE_NOTE, it) })
            notesLiveData.postValue(list)
        }
    }

    @SuppressLint("CheckResult")
    fun submit(text: String) {
        val note = Note()
        note.questionId = selectedQuestion?.id
        note.branchNumber = branchNumber
        note.countyCode = countyCode
        note.description = text
        note.uriPath = noteFile?.absolutePath
        repository.saveNote(note).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).doFinally {
                //Writing to database is successful and we don't really need the result of the network call
                submitCompletedLiveData.postValue(true)
            }.subscribe({},
                {
                    Log.d(TAG, it.toString())
                })
    }

    fun getMediaFromGallery(uri: Uri?) {
        uri?.let {
            val filePath = FileUtils.getPath(app, it)
            if (filePath != null) {
                val file = File(filePath)
                fileNameLiveData.postValue(file.name)
                noteFile = file
            } else {
                messageIdToastLiveData.postValue(app.getString(ro.code4.monitorizarevot.R.string.error_permission_external_storage))
            }
        }
    }

    fun addFile(file: File?) {
        noteFile = file
    }

    fun addMediaToGallery() {
        fileNameLiveData.postValue(noteFile?.name)
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val contentUri = Uri.fromFile(noteFile)
        mediaScanIntent.data = contentUri
        app.sendBroadcast(mediaScanIntent)
    }

}