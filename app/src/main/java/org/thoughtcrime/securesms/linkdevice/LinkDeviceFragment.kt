package org.thoughtcrime.securesms.linkdevice

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.DialogState
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Locale

private const val PLACEHOLDER = "__ICON_PLACEHOLDER__"

/**
 * Fragment that shows current linked devices
 */
class LinkDeviceFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(LinkDeviceFragment::class)
  }

  private val viewModel: LinkDeviceViewModel by activityViewModels()
  private lateinit var biometricAuth: BiometricDeviceAuthentication
  private lateinit var biometricDeviceLockLauncher: ActivityResultLauncher<String>

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.initialize()

    biometricDeviceLockLauncher = registerForActivityResult(BiometricDeviceLockContract()) { result: Int ->
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
      }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(requireContext().getString(R.string.LinkDeviceFragment__unlock_to_link))
      .setConfirmationRequired(true)
      .build()
    biometricAuth = BiometricDeviceAuthentication(
      BiometricManager.from(requireActivity()),
      BiometricPrompt(requireActivity(), BiometricAuthenticationListener()),
      promptInfo
    )
  }

  override fun onPause() {
    super.onPause()
    biometricAuth.cancelAuthentication()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val context = LocalContext.current

    LaunchedEffect(state.oneTimeEvent) {
      when (val event = state.oneTimeEvent) {
        LinkDeviceSettingsState.OneTimeEvent.None -> {
          Unit
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastLinked -> {
          Toast.makeText(context, context.getString(R.string.LinkDeviceFragment__s_linked, event.name), Toast.LENGTH_LONG).show()
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastUnlinked -> {
          Toast.makeText(context, context.getString(R.string.LinkDeviceFragment__s_unlinked, event.name), Toast.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.ToastNetworkFailed -> {
          Toast.makeText(context, context.getString(R.string.DeviceListActivity_network_failed), Toast.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.LaunchQrCodeScanner -> {
          navController.navigateToQrScannerIfAuthed()
        }
        LinkDeviceSettingsState.OneTimeEvent.ShowFinishedSheet -> {
          navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceFinishedSheet)
        }
        LinkDeviceSettingsState.OneTimeEvent.HideFinishedSheet -> {
          if (navController.currentDestination?.id == R.id.linkDeviceFinishedSheet) {
            navController.popBackStack()
          }
        }
      }

      if (state.oneTimeEvent != LinkDeviceSettingsState.OneTimeEvent.None) {
        viewModel.clearOneTimeEvent()
      }
    }

    LaunchedEffect(state.seenEducationSheet) {
      if (state.seenEducationSheet) {
        if (!biometricAuth.authenticate(requireContext(), true) { biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link)) }) {
          navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
        }
        viewModel.markEducationSheetSeen(false)
      }
    }

    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences__linked_devices),
      onNavigationClick = { navController.popOrFinish() },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      DeviceListScreen(
        state = state,
        modifier = Modifier.padding(contentPadding),
        onLearnMoreClicked = { navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceLearnMoreBottomSheet) },
        onLinkNewDeviceClicked = { navController.navigateToQrScannerIfAuthed() },
        onDeviceSelectedForRemoval = { device -> viewModel.setDeviceToRemove(device) },
        onDeviceRemovalConfirmed = { device -> viewModel.removeDevice(device) },
        onSyncFailureRetryRequested = { deviceId -> viewModel.onSyncErrorRetryRequested(deviceId) },
        onSyncFailureIgnored = { viewModel.onSyncErrorIgnored() }
      )
    }
  }

  private fun NavController.navigateToQrScannerIfAuthed() {
    if (biometricAuth.canAuthenticate(requireContext())) {
      this.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceEducationSheet)
    } else {
      this.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
    }
  }

  private fun NavController.popOrFinish() {
    if (!popBackStack()) {
      requireActivity().finishAfterTransition()
    }
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Authentication error: $errorCode")
      onAuthenticationFailed()
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Authentication succeeded")
      findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Unable to authenticate")
    }
  }
}

@Composable
fun DeviceListScreen(
  state: LinkDeviceSettingsState,
  modifier: Modifier = Modifier,
  onLearnMoreClicked: () -> Unit = {},
  onLinkNewDeviceClicked: () -> Unit = {},
  onDeviceSelectedForRemoval: (Device?) -> Unit = {},
  onDeviceRemovalConfirmed: (Device) -> Unit = {},
  onSyncFailureRetryRequested: (Int?) -> Unit = {},
  onSyncFailureIgnored: () -> Unit = {}
) {
  // If a bottom sheet is showing, we don't want the spinner underneath
  if (!state.bottomSheetVisible) {
    when (state.dialogState) {
      DialogState.None -> {
        Unit
      }
      DialogState.Linking -> {
        Dialogs.IndeterminateProgressDialog(stringResource(id = R.string.LinkDeviceFragment__linking_device))
      }
      DialogState.Unlinking -> {
        Dialogs.IndeterminateProgressDialog(stringResource(id = R.string.DeviceListActivity_unlinking_device))
      }
      DialogState.SyncingMessages -> {
        Dialogs.IndeterminateProgressDialog(stringResource(id = R.string.LinkDeviceFragment__syncing_messages))
      }
      is DialogState.SyncingFailed,
      DialogState.SyncingTimedOut -> {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.LinkDeviceFragment__sync_failure_title),
          body = stringResource(R.string.LinkDeviceFragment__sync_failure_body),
          confirm = stringResource(R.string.LinkDeviceFragment__sync_failure_retry_button),
          onConfirm = {
            if (state.dialogState is DialogState.SyncingFailed) {
              onSyncFailureRetryRequested(state.dialogState.deviceId)
            } else {
              onSyncFailureRetryRequested(null)
            }
          },
          dismiss = stringResource(R.string.LinkDeviceFragment__sync_failure_dismiss_button),
          onDismiss = onSyncFailureIgnored
        )
      }
    }
  }

  if (state.deviceToRemove != null) {
    val device: Device = state.deviceToRemove
    val name = if (device.name.isNullOrEmpty()) stringResource(R.string.DeviceListItem_unnamed_device) else device.name
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.DeviceListActivity_unlink_s, name),
      body = stringResource(id = R.string.DeviceListActivity_by_unlinking_this_device_it_will_no_longer_be_able_to_send_or_receive),
      confirm = stringResource(R.string.LinkDeviceFragment__unlink),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onDeviceRemovalConfirmed(device) },
      onDismiss = { onDeviceSelectedForRemoval(null) }
    )
  }

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.verticalScroll(rememberScrollState())) {
    Icon(
      painter = painterResource(R.drawable.ic_devices_intro),
      contentDescription = stringResource(R.string.preferences__linked_devices),
      tint = Color.Unspecified
    )
    Text(
      text = stringResource(id = R.string.LinkDeviceFragment__use_molly_on_another_devices),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
    )
    ClickableText(
      text = AnnotatedString(stringResource(id = R.string.LearnMoreTextView_learn_more)),
      style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
    ) {
      onLearnMoreClicked()
    }

    Spacer(modifier = Modifier.size(20.dp))

    Buttons.LargeTonal(
      onClick = onLinkNewDeviceClicked,
      modifier = Modifier.defaultMinSize(300.dp).padding(bottom = 8.dp)
    ) {
      Text(stringResource(id = R.string.LinkDeviceFragment__link_a_new_device))
    }

    Dividers.Default()

    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = stringResource(R.string.LinkDeviceFragment__my_linked_devices),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp)
      )
      if (state.deviceListLoading) {
        Spacer(modifier = Modifier.size(30.dp))
        CircularProgressIndicator(
          modifier = Modifier
            .size(36.dp)
            .align(Alignment.CenterHorizontally),
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(30.dp))
      } else if (state.devices.isEmpty()) {
        Text(
          text = stringResource(R.string.LinkDeviceFragment__no_linked_devices),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .wrapContentHeight(align = Alignment.CenterVertically)
        )
      } else {
        state.devices.forEach { device ->
          DeviceRow(device, onDeviceSelectedForRemoval)
        }
      }
    }

    Row(
      modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val message = stringResource(id = R.string.LinkDeviceFragment__messages_and_chat_info_are_protected, PLACEHOLDER)
      val (messageText, messageInline) = remember(message) {
        val parts = message.split(PLACEHOLDER)
        val annotatedString = buildAnnotatedString {
          append(parts[0])
          appendInlineContent("icon")
          append(parts[1])
        }

        val inlineContentMap = mapOf(
          "icon" to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
            Image(
              imageVector = ImageVector.vectorResource(id = R.drawable.symbol_lock_24),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
          }
        )

        annotatedString to inlineContentMap
      }

      Text(
        text = messageText,
        inlineContent = messageInline,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun DeviceRow(device: Device, setDeviceToRemove: (Device) -> Unit) {
  val titleString = if (device.name.isNullOrEmpty()) stringResource(R.string.DeviceListItem_unnamed_device) else device.name
  val linkedDate = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, Locale.getDefault(), device.createdMillis)
  val lastActive = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, Locale.getDefault(), device.lastSeenMillis)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { setDeviceToRemove(device) },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      painter = painterResource(id = R.drawable.symbol_devices_24),
      contentDescription = null,
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
      contentScale = ContentScale.Inside,
      modifier = Modifier
        .padding(start = 24.dp, top = 28.dp, bottom = 28.dp)
        .size(40.dp)
        .background(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = CircleShape
        )
    )
    Spacer(modifier = Modifier.size(16.dp))
    Column {
      Text(text = titleString, style = MaterialTheme.typography.bodyLarge)
      Text(stringResource(R.string.DeviceListItem_linked_s, linkedDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(stringResource(R.string.DeviceListItem_last_active_s, lastActive), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        devices = listOf(
          Device(1, "Sam's Macbook Pro", 1715793982000, 1716053182000),
          Device(1, "Sam's iPad", 1715793182000, 1716053122000)
        )
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenLoadingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        deviceListLoading = true
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenLinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Linking
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenUnlinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Unlinking
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenSyncingMessagesPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingMessages
      )
    )
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenSyncingFailedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingTimedOut
      )
    )
  }
}
