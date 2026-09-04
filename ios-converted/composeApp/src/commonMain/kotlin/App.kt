import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.usinagemmaster.game.domain.GameStore
import br.com.usinagemmaster.game.ui.GameApp

@Composable
fun App() {
    val store = remember { GameStore() }
    GameApp(store)
}
