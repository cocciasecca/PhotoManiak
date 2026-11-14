package app.inphotomaniak

internal data class CastingState(val ready: Boolean=false,val unavailable: String?=null,val connected: Boolean=false,
    val device: String="",val title: String="",val loading: Boolean=false,val video: Boolean=false,
    val playing: Boolean=false,val position: Long=0,val duration: Long=0,val error: String?=null)

