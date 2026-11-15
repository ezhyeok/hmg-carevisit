package dev.carevisit.app
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class StoreTest {
    @Test fun launchBeforeServiceCollectorIsNotLost()=runTest {VisitStore.commands.trySend(AppCommand.Demo(true));assertEquals(AppCommand.Demo(true),VisitStore.commands.receive())}
}
