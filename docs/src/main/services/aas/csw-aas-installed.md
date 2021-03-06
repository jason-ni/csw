# Installed Auth Adapter (csw-aas-installed)

`csw-aas-installed` is the adapter you will use if you want to build an application that executes on user's 
machine & talks to auth-protected web service application. Examples of such applications 
could be a CLI app that is installed on end users machine.

## Dependencies

To use the Akka HTTP Adapter (csw-aas-installed), add this to your `build.sbt` file:

sbt
:   @@@vars
    ```scala
    libraryDependencies += "com.github.tmtsoftware.csw" %% "csw-aas-installed" % "$version$"
    ```
    @@@

## Prerequisites

To run a client app that is installed on user's machine, which needs to talk to a protected 
http server, we need

* location service running
* Keycloak instance running and registered with location service
* protected http server running

All of these can be running on different machines. To start location service & keycloak 
server on a local machine, you can make use of `csw-services.sh` script.

## Application Configurations

All auth related configurations go inside `auth-config` block. There are two configurations 
applicable for a public cli client application i.e. `realm` & `client-id`.

`realm` has a
default value of `TMT` if not specified. Ideally all apps in TMT should not have to override
this, however it might be useful to override this while testing your app.

`client-id` is a mandatory configuration which specifies the client id of the app as per registration
in keycloak.

```hocon
auth-config {
  realm = TMT # DEFAULT
  client-id = demo-cli # REQUIRED
}
```

## Building a CLI Application

Let's say that we have an existing akka-http application which has some open and 
some protected routes and we want to build a CLI client which accesses these routes.

This is what the routes look like:

Scala
:   @@snip [Routes](../../../../../examples/src/main/scala/example/auth/installed/SampleRoutes.scala) { #sample-routes }

@@@ note
To know more about how to create secure web apis, please go through 
@ref:[Akka HTTP Adapter - csw-aas-http](csw-aas-http.md)
@@@

We will create a CLI application that has following commands:

| command         | description                  |
| :-------------- | :--------------------------- |
| login           | performs user authentication |
| logout          | logs user out                |
| read            | reads data from server       |
| write {content} | writes data to server        |

Let's begin with `Main.scala`

Scala
:   @@snip [Main](../../../../../examples/src/main/scala/example/auth/installed/Main.scala) { #main-app }

The statement `LocationServerStatus.requireUpLocally()` ensures that location service is up and running
before proceeding further. If location service is not running, it will throw an exception and exit the 
application.

@@@ note
In a real application, you would ideally want to use `LocationServerStatus.requireUp` which takes
`locationHost: String` parameter instead of looking for location service on localhost. 
@@@

Next, we will instantiate `InstalledAppAuthAdapter`. There is a factory already available to create the 
required instance. We will create a small factory on top this factory to keep our Main.scala clean.

Scala
:   @@snip [Adapter-Factory](../../../../../examples/src/main/scala/example/auth/installed/AdapterFactory.scala) { #adapter-factory }

Note the the internal factory overload we have used, requires two parameters, i.e. location service & authStore.
It needs location service to resolve keycloak server. FileAuthStore is just a storage for tokens for it to 
save access tokens & refresh tokens in file system. 

@@@ warning { title='Warning' }
In this case we have configured it to store all tokens in "/tmp/demo-cli/auth" 
directory but ideally you want this location to be somewhere in user's home directory.
This will ensure that different users don't have access to each other's tokens.
@@@

Coming back to Main.scala, now we need to find out which command user wants to execute. To parse 
user input arguments, we will create a small utility.

Scala
:   @@snip [Command-Factory](../../../../../examples/src/main/scala/example/auth/installed/commands/CommandFactory.scala) { #command-factory }
 
All of these commands extend from a simple trait - `AppCommand`

Scala
:   @@snip [AppCommand](../../../../../examples/src/main/scala/example/auth/installed/commands/AppCommand.scala) { #app-command }

@@@ note
We could have used a command line parser library here to parse the command names and options/arguments, but since 
our requirements are simple and this is a demonstration, we will keep things simple. However, we 
strongly recommend that you use one of the existing libraries. CSW makes extensive use of 
[scopt](https://github.com/scopt/scopt). There are other libraries which are equally good and easy to use

@@@

Let's go through each command one by one   

### Login

Scala
:   @@snip [LoginCommand](../../../../../examples/src/main/scala/example/auth/installed/commands/LoginCommand.scala) { #login-command }

Here the constructor takes InstalledAppAuthAdapter as a parameter and in the run method, 
it calls `installedAppAuthAdapter.login()`. This method, opens a browser and redirects user
to TMT login screen (served by keycloak). In the background, it starts an http server
on a random port. Once the user submits correct credentials on the login screen, keycloak
redirects user to `http://localhost:[RandomPort]` with the access and refresh tokens in 
query string. The InstalledAppAuthAdapter will then save these token in file system using 
FileAuthStore. After this, InstalledAppAuthAdapter will shut down the local server since it's
purpose is served. Now, user can close the browser.

If you want to develop an CLI app that is not dependent on browser, you can call
`loginCommandLine()` method instead of `login()`. This will prompt credentials in CLI 
instead of opening a browser.

@@@ note 
It may be tempting to use the `loginCommandLine()` method, however a browser is generally more
user-friendly since it can store cookies & remember passwords.
@@@

### Logout

Scala
:   @@snip [LogoutCommand](../../../../../examples/src/main/scala/example/auth/installed/commands/LogoutCommand.scala) { #logout-command }

The structure here is very similar to login command. `installedAppAuthAdapter.logout()` 
clears all the tokens from file system via `FileAuthStore`.

### Read

Scala
:   @@snip [ReadCommand](../../../../../examples/src/main/scala/example/auth/installed/commands/ReadCommand.scala) { #read-command }

Since in the akka-http routes, the get route is not protected by any authentication or
authorization, read command simply sends a get request and prints the response.

### Write

Scala
:   @@snip [WriteCommand](../../../../../examples/src/main/scala/example/auth/installed/commands/WriteCommand.scala) { #write-command }

Write command constructor takes InstalledAppAuthAdapter & a string value. This string value is expected
from the CLI input. Since in the akka-http routes, the post route is protected by a realm role policy, we need to pass
bearer token in the request header. 

`installedAppAuthAdapter.getAccessTokenString()` returns `Option[String]` if it is None, it means 
that user has not logged in and so we display an error message stating the same. If it returns a token string, 
we pass it in the header.

If the response status code is 200, it means authentication and authorization were successful. In this case
authorization required that the user had `admin` role. 

If the response is 401, it indicates that there was something wrong with the token. 
It could indicate that token is expired or does not have valid signature. 
`InstalledAppAuthAdapter` ensures that you don't send a request with an expired token.
If the access token is expired, it refreshes the access token with the help of a `refresh` token.
If the refresh token is also expired, it returns `None` which means that user has to log in again.

If the response is 403, it indicates that token was valid but the token is not authorized to 
perform certain action. In this case if the token belonged to a user who does not have `admin`
role, server will return 403.

## Source code for above examples

@github[Example](/examples/src/main/scala/example/auth/installed)
