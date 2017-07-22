package de.stefanmedack.ccctv.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.v17.leanback.app.RowsFragment
import android.support.v17.leanback.widget.*
import android.support.v4.app.ActivityOptionsCompat
import de.stefanmedack.ccctv.ui.details.DetailsActivity
import de.stefanmedack.ccctv.util.EVENT
import de.stefanmedack.ccctv.util.applySchedulers
import info.metadude.kotlin.library.c3media.ApiModule
import info.metadude.kotlin.library.c3media.RxC3MediaService
import info.metadude.kotlin.library.c3media.models.Conference
import info.metadude.kotlin.library.c3media.models.Event
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toObservable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@SuppressLint("ValidFragment")
class ConferencesFragment(val conferenceStubs: List<Conference>) : RowsFragment() {

    lateinit var mDisposables: CompositeDisposable
    private val mRowsAdapter: ArrayObjectAdapter = ArrayObjectAdapter(ListRowPresenter())

    init {
        adapter = mRowsAdapter
        onItemViewClickedListener = ItemViewClickedListener()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConferencesAsync()
        mainFragmentAdapter.fragmentHost.notifyDataReady(mainFragmentAdapter)
    }

    override fun onDestroy() {
        mDisposables.clear()
        super.onDestroy()
    }

    private fun renderConferences(conferences: MutableList<Conference>) {
        for (conference in conferences) {
            mRowsAdapter.add(createEventRow(conference))
        }
    }

    private fun createEventRow(conference: Conference?): Row {
        val presenterSelector = CardPresenter()
        val adapter = ArrayObjectAdapter(presenterSelector)
        for (event in conference?.events ?: listOf()) {
            adapter.add(event)
        }

        val headerItem = HeaderItem(conference?.title)
        return ListRow(headerItem, adapter)
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                                   rowViewHolder: RowPresenter.ViewHolder, row: Row) {
            if (item is Event) {
                val intent = Intent(activity, DetailsActivity::class.java)
                intent.putExtra(EVENT, item)

                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity,
                        (itemViewHolder.view as ImageCardView).mainImageView,
                        DetailsActivity.SHARED_ELEMENT_NAME).toBundle()
                activity.startActivity(intent, bundle)
            }
        }
    }

    private fun loadConferencesAsync() {
        val loadConferencesSingle = conferenceStubs.toObservable()
                .map { it.url?.substringAfterLast('/')?.toInt() ?: -1 }
                .filter { it > 0 }
                .flatMap {
                    service.getConference(it)
                            .applySchedulers()
                            .toObservable()
                }
                .toSortedList(compareByDescending(Conference::title))

        mDisposables = CompositeDisposable()
        mDisposables.add(loadConferencesSingle
                .subscribeBy(// named arguments for lambda Subscribers
                        onSuccess = { renderConferences(it) },
                        // TODO proper error handling
                        onError = { it.printStackTrace() }
                ))
    }

    private val service: RxC3MediaService by lazy {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.NONE
        val okHttpClient = OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                .build()
        ApiModule.provideRxC3MediaService("https://api.media.ccc.de", okHttpClient)
    }
}