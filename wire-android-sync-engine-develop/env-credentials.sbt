import SharedSettings._

def getEnv(key: String) = Option(System.getenv(key)).getOrElse("")

emailTestUser in ThisBuild := EmailTestUser(getEnv("TEST_EMAIL"), getEnv("TEST_PASSWD"))
internalBackend in ThisBuild := InternalBackendPasswords(getEnv("STAGING_ENV"))
