resource "google_artifact_registry_repository" "pr_bot" {
  location      = var.region # must be europe-west2 to match your image path
  repository_id = "pr-bot"   # the FIRST "pr-bot" in your image path
  format        = "DOCKER"
  description   = "Container images for the PR review bot"
  depends_on    = [google_project_service.apis]
}
resource "google_pubsub_topic" "reviews" {
  name       = "pr-reviews"
  depends_on = [google_project_service.apis]
}

# Identity Pub/Sub uses to call your worker endpoint
resource "google_service_account" "pubsub_invoker" {
  account_id   = "pubsub-invoker"
  display_name = "Pub/Sub push invoker"
}

# Only this identity may invoke the Cloud Run service
resource "google_cloud_run_v2_service_iam_member" "invoker" {
  name     = google_cloud_run_v2_service.pr_bot.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.pubsub_invoker.email}"
}

resource "google_pubsub_topic_iam_member" "app_can_publish" {
  topic  = google_pubsub_topic.reviews.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.app.email}"
}
# Let the Pub/Sub service agent mint OIDC tokens as the invoker SA
resource "google_project_service_identity" "pubsub_agent" {
  provider = google-beta
  service  = "pubsub.googleapis.com"
}
resource "google_service_account_iam_member" "token_creator" {
  service_account_id = google_service_account.pubsub_invoker.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_project_service_identity.pubsub_agent.email}"
}

resource "google_pubsub_subscription" "reviews_push" {
  name  = "pr-reviews-push"
  topic = google_pubsub_topic.reviews.id

  push_config {
    push_endpoint = "${google_cloud_run_v2_service.pr_bot.uri}/tasks/review"
    oidc_token {
      service_account_email = google_service_account.pubsub_invoker.email
      audience              = google_cloud_run_v2_service.pr_bot.uri
    }
  }

  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
}