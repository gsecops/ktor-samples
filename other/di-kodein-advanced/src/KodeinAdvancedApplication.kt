package io.ktor.samples.kodein

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.html.*
import org.kodein.di.*
import org.kodein.di.generic.*

/**
 * Entry point of the embedded-server sample program:
 *
 * io.ktor.samples.kodein.KodeinAdvancedApplicationKt.main
 *
 * This would start and wait a web-server at port 8080 using Netty.
 *
 * Uses the included [kodeinApplication] function
 * to register a more complex application that will
 * automatically detect mapped [KodeinController] subtypes
 * and will register the declared routes.
 */
fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        kodeinApplication { application ->
            advancedApplication(application)
        }
    }.start(wait = true)
}

internal fun Kodein.MainBuilder.advancedApplication(application: Application) {
    application.apply {
        // This adds automatically Date and Server headers to each response, and would allow you to configure
        // additional headers served to each response.
        install(DefaultHeaders)
    }

    bind<Users.IRepository>() with singleton { Users.Repository() }
    bind<Users.Controller>() with singleton { Users.Controller(kodein) }
}

/**
 * Users Controller, Router and Model. Can move to several files and packages if required.
 */
object Users {
    /**
     * The Users controller. This controller handles the routes related to users.
     * It inherits [KodeinController] that offers some basic functionality.
     * It only requires a [kodein] instance.
     */
    class Controller(kodein: Kodein) : KodeinController(kodein) {
        /**
         * [Repository] instance provided by [Kodein]
         */
        private val repository: IRepository by instance()

        /**
         * Registers the routes related to [Users].
         */
        override fun Routing.registerRoutes() {
            /**
             * GET route for [Routes.Users] /users, it responds
             * with a HTML listing all the users in the repository.
             */
            get<Routes.Users> {
                call.respondHtml {
                    body {
                        ul {
                            for (user in repository.list()) {
                                li { a(Routes.User(user.name).href) { +user.name } }
                            }
                        }
                    }
                }
            }

            /**
             * GET route for [Routes.User] /users/{name}, it responds
             * with a HTML showing the provided user by [Routes.User.name].
             */
            get<Routes.User> { user ->
                call.respondHtml {
                    body {
                        h1 { +user.name }
                    }
                }
            }
        }
    }

    /**
     * Data class representing a [User] by its [name].
     */
    data class User(val name: String)

    /**
     * Repository that will handle operations related to the users on the system.
     */
    interface IRepository {
        fun list(): List<User>
    }

    /**
     * Fake in-memory implementation of [Users.IRepository] for demo purposes.
     */
    class Repository : IRepository {
        private val initialUsers = listOf(User("test"), User("demo"))
        private val usersByName = initialUsers.associateBy { it.name }

        /**
         * Lists the available [Users.User] in this repository.
         */
        override fun list() = usersByName.values.toList()
    }

    /**
     * A class containing routes annotated with [Location] and implementing [TypedRoute].
     */
    object Routes {
        /**
         * Route for listing users.
         */
        @Location("/users")
        object Users : TypedRoute

        /**
         * Route for showing a specific user from its [name].
         */
        @Location("/users/{name}")
        data class User(val name: String) : TypedRoute
    }
}

// Extensions

/**
 * Registers a [kodeinApplication] that that will call [kodeinMapper] for mapping stuff.
 * The [kodeinMapper] is a lambda that is in charge of mapping all the required.
 *
 * After calling [kodeinMapper], this function will search
 * for registered subclasses of [KodeinController], and will call their [KodeinController.registerRoutes] methods.
 */
fun Application.kodeinApplication(
    kodeinMapper: Kodein.MainBuilder.(Application) -> Unit = {}
) {
    val application = this

    // Allows to use classes annotated with @Location to represent URLs.
    // They are typed, can be constructed to generate URLs, and can be used to register routes.
    application.install(Locations)

    /**
     * Creates a [Kodein] instance, binding the [Application] instance.
     * Also calls the [kodeinMapper] to map the Controller dependencies.
     */
    val kodein = Kodein {
        bind<Application>() with instance(application)
        kodeinMapper(this, application)
    }

    /**
     * Detects all the registered [KodeinController] and registers its routes.
     */
    routing {
        fun findControllers(kodein: Kodein): List<KodeinController> =
            kodein
                .container.tree.bindings.keys
                .filter { bind ->
                    val clazz = bind.type.jvmType as? java.lang.Class<*> ?: return@filter false
                    KodeinController::class.java.isAssignableFrom(clazz)
                }
                .map { bind ->
                    val res by kodein.Instance(bind.type)
                    res as KodeinController
                }

        findControllers(kodein).forEach { controller ->
            println("Registering '$controller' routes...")
            controller.apply { registerRoutes() }
        }
    }
}

/**
 * A [KodeinAware] base class for Controllers handling routes.
 * It allows to easily get dependencies, and offers some useful extensions like getting the [href] of a [TypedRoute].
 */
abstract class KodeinController(override val kodein: Kodein) : KodeinAware {
    /**
     * Injected dependency with the current [Application].
     */
    private val application: Application by instance()

    /**
     * Shortcut to get the url of a [TypedRoute].
     */
    val TypedRoute.href get() = application.locations.href(this)

    /**
     * Method that subtypes must override to register the handled [Routing] routes.
     */
    abstract fun Routing.registerRoutes()
}

/**
 * Interface used for identify typed routes annotated with [Location].
 */
interface TypedRoute
