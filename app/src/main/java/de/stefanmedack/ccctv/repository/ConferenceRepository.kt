package de.stefanmedack.ccctv.repository

import de.stefanmedack.ccctv.model.Resource
import de.stefanmedack.ccctv.persistence.daos.ConferenceDao
import de.stefanmedack.ccctv.persistence.daos.EventDao
import de.stefanmedack.ccctv.persistence.entities.ConferenceWithEvents
import de.stefanmedack.ccctv.persistence.preferences.C3SharedPreferences
import de.stefanmedack.ccctv.persistence.separateLists
import de.stefanmedack.ccctv.persistence.toEntity
import de.stefanmedack.ccctv.util.applySchedulers
import de.stefanmedack.ccctv.util.id
import info.metadude.kotlin.library.c3media.RxC3MediaService
import io.reactivex.Flowable
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConferenceRepository @Inject constructor(
        private val mediaService: RxC3MediaService,
        private val conferenceDao: ConferenceDao,
        private val eventDao: EventDao,
        private val preferences: C3SharedPreferences
) {
    val conferences: Flowable<Resource<List<ConferenceEntity>>>
        get() = object : NetworkBoundResource<List<ConferenceEntity>, List<ConferenceRemote>>() {

            override fun fetchLocal(): Flowable<List<ConferenceEntity>> = conferenceDao.getConferences()

            override fun saveLocal(data: List<ConferenceEntity>) = conferenceDao.insertAll(data)

            override fun isStale(localResource: Resource<List<ConferenceEntity>>) = when (localResource) {
                is Resource.Error -> true
                is Resource.Loading -> false
                is Resource.Success -> localResource.data.isEmpty()
            }

            override fun fetchNetwork(): Single<List<ConferenceRemote>> = mediaService
                    .getConferences()
                    .map { it.conferences?.filterNotNull() }

            override fun mapNetworkToLocal(data: List<ConferenceRemote>) = data.mapNotNull { it.toEntity() }

        }.resource

    fun conferenceWithEvents(conferenceId: Int): Flowable<Resource<ConferenceWithEvents>>
            = object : NetworkBoundResource<ConferenceWithEvents, ConferenceRemote>() {

        override fun fetchLocal(): Flowable<ConferenceWithEvents> = conferenceDao.getConferenceWithEventsById(conferenceId)

        override fun saveLocal(data: ConferenceWithEvents) =
                data.let { (_, events) ->
                    eventDao.insertAll(events)
                    preferences.updateLatestDataFetchDate()
                }

        override fun isStale(localResource: Resource<ConferenceWithEvents>) = when (localResource) {
            is Resource.Success -> localResource.data.events.isEmpty() || preferences.isFetchedDataStale()
            is Resource.Loading -> false
            is Resource.Error -> true
        }

        override fun fetchNetwork(): Single<ConferenceRemote> = mediaService.getConference(conferenceId)
                .applySchedulers()

        override fun mapNetworkToLocal(data: ConferenceRemote): ConferenceWithEvents =
                data.toEntity()?.let { conference ->
                    ConferenceWithEvents(
                            conference = conference,
                            events = data.events?.mapNotNull { it?.toEntity(conferenceId) } ?: listOf()
                    )
                } ?: throw IllegalArgumentException("Could not parse ConferenceRemote to ConferenceEntity")
    }.resource

    val conferencesWithEvents: Flowable<Resource<List<ConferenceWithEvents>>>
        get() = object : NetworkBoundResource<List<ConferenceWithEvents>, List<ConferenceRemote>>() {

            override fun fetchLocal(): Flowable<List<ConferenceWithEvents>> = conferenceDao.getConferencesWithEvents()

            override fun saveLocal(data: List<ConferenceWithEvents>) =
                    data.separateLists().let { (conferences, events) ->
                        conferenceDao.insertConferencesWithEvents(conferences, events)
                        preferences.updateLatestDataFetchDate()
                    }

            override fun isStale(localResource: Resource<List<ConferenceWithEvents>>) = when (localResource) {
                is Resource.Success -> localResource.data.isEmpty() || preferences.isFetchedDataStale()
                is Resource.Loading -> false
                is Resource.Error -> true
            }

            override fun fetchNetwork(): Single<List<ConferenceRemote>> = mediaService
                    .getConferences()
                    .toFlowable()
                    .map { it.conferences }
                    .flatMapIterable { it }
                    .flatMap {
                        it.id()?.let {
                            mediaService.getConference(it)
                                    .applySchedulers()
                                    .toFlowable()
                        }
                    }
                    .toList()

            override fun mapNetworkToLocal(data: List<ConferenceRemote>) = data.mapNotNull { remoteConference ->
                remoteConference.toEntity()?.let { entityConf ->
                    ConferenceWithEvents(
                            entityConf,
                            remoteConference.events?.mapNotNull { it?.toEntity(entityConf.id) } ?: listOf()
                    )
                }
            }

        }.resource

    fun loadedConferences(conferenceGroup: String): Flowable<Resource<List<ConferenceEntity>>> = conferenceDao
            .getConferences(conferenceGroup.toLowerCase())
            .map<Resource<List<ConferenceEntity>>> { Resource.Success(it) }
            .applySchedulers()

}
