import '../../client/src/style.css';
import { redirectLobbyToMultiplayer, showBootError, startMultiplayer } from '../../client/src/bootstrap';

redirectLobbyToMultiplayer();
void startMultiplayer().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : 'Could not start the multiplayer room.';
  showBootError(message);
});
