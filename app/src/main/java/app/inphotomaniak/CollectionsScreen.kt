package app.inphotomaniak

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun CollectionsScreen(s: GalleryState, model: GalleryModel) {
    var newAlbum by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<VirtualAlbum?>(null) }
    var remove by remember { mutableStateOf<VirtualAlbum?>(null) }
    val available=remember(s.assets) { s.assets.map { it.entry.collectionId() }.toSet() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.collections),style=MaterialTheme.typography.headlineMedium) }
        item { ListItem(headlineContent={Text(stringResource(R.string.favorites))},leadingContent={Icon(Icons.Outlined.FavoriteBorder,null)},
            supportingContent={Text(stringResource(R.string.collection_count,s.collections.favorites.count { it in available }))},
            modifier=Modifier.clickable { model.collectionView("favorites") }) }
        item { ListItem(headlineContent={Text(stringResource(R.string.archive))},leadingContent={Icon(Icons.Outlined.Archive,null)},
            supportingContent={Text(stringResource(R.string.collection_count,s.collections.archived.count { it in available }))},
            modifier=Modifier.clickable { model.collectionView("archive") }) }
        item { Text(stringResource(R.string.albums),style=MaterialTheme.typography.titleLarge) }
        item { Text(stringResource(R.string.virtual_albums_help),style=MaterialTheme.typography.bodySmall) }
        item { Button(enabled=!s.busy,onClick={newAlbum=true}) { Icon(Icons.Outlined.Add,null);Text(stringResource(R.string.new_album)) } }
        items(s.collections.albums,key={it.id}) { album ->
            ListItem(headlineContent={Text(album.name)},supportingContent={Text(stringResource(R.string.collection_count,album.members.count { it in available }))},
                leadingContent={Icon(Icons.Outlined.PhotoAlbum,null)},modifier=Modifier.clickable { model.collectionView("album",album.id) },
                trailingContent={ Row {
                    IconButton(enabled=!s.busy,onClick={rename=album}) { Icon(Icons.Outlined.Edit,stringResource(R.string.rename_album)) }
                    IconButton(enabled=!s.busy,onClick={remove=album}) { Icon(Icons.Outlined.DeleteOutline,stringResource(R.string.delete_album)) }
                } })
        }
    }
    if(newAlbum) AlbumNameDialog(onDismiss={newAlbum=false}) { model.createAlbum(it);newAlbum=false }
    rename?.let { album -> AlbumNameDialog(album.name,onDismiss={rename=null}) { model.renameAlbum(album.id,it);rename=null } }
    remove?.let { album -> AlertDialog(onDismissRequest={remove=null},title={Text(stringResource(R.string.delete_album_title,album.name))},
        text={Text(stringResource(R.string.delete_album_help))},confirmButton={TextButton(onClick={model.deleteAlbum(album.id);remove=null}) { Text(stringResource(R.string.delete_album)) }},
        dismissButton={TextButton(onClick={remove=null}) { Text(stringResource(R.string.cancel)) }}) }
}

@Composable internal fun AlbumNameDialog(initial: String = "", onDismiss: ()->Unit, onSave: (String)->Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(initial.isEmpty()) R.string.new_album else R.string.rename_album))},
        text={OutlinedTextField(value=name,onValueChange={if(it.length<=100) name=it},singleLine=true,label={Text(stringResource(R.string.album_name))})},
        confirmButton={TextButton(enabled=name.isNotBlank(),onClick={onSave(name.trim())}) { Text(stringResource(R.string.save)) }},
        dismissButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }})
}

@Composable internal fun AddToAlbumDialog(s: GalleryState, model: GalleryModel, assets: List<Asset>, onDismiss: ()->Unit) {
    var creating by remember { mutableStateOf(false) }
    if(creating) AlbumNameDialog(onDismiss={creating=false}) { model.createAlbum(it,assets);onDismiss() }
    else AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.add_to_album))},text={
        LazyColumn(Modifier.heightIn(max=360.dp)) {
            item { TextButton(onClick={creating=true}) { Text(stringResource(R.string.new_album)) } }
            items(s.collections.albums,key={it.id}) { album ->
                ListItem(headlineContent={Text(album.name)},modifier=Modifier.clickable { model.albumMembers(album.id,assets,true);onDismiss() })
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) }})
}
