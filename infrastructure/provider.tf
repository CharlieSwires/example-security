provider "katapult" {
  # Credentials and location are deliberately supplied through:
  #   KATAPULT_API_KEY
  #   KATAPULT_ORGANIZATION
  #   KATAPULT_DATA_CENTER=uk-lon-01
  #
  # Destruction is guarded on the paid resources themselves. If those guards
  # are deliberately removed at the end of the test, purge trashed objects so
  # they are not unintentionally retained beyond the one-month exercise.
  skip_trash_object_purge = false
}
