# Enable the APIs you'll need — Terraform fails cryptically if these are off
resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",                  # Cloud Run
    "pubsub.googleapis.com",               # Pub/Sub
    "secretmanager.googleapis.com",        # Secret Manager
    "artifactregistry.googleapis.com",     # GAR
    "iam.googleapis.com",                  # service accounts / IAM
    "cloudresourcemanager.googleapis.com", # often needed for IAM bindings
  ])
  service            = each.value
  disable_on_destroy = false
}
resource "google_cloud_run_v2_service_iam_member" "public" {
  name     = google_cloud_run_v2_service.pr_bot.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}
resource "google_secret_manager_secret" "anthropic_key" {
  secret_id = "anthropic-api-key"
  replication {
    auto {}
  }
}
resource "google_secret_manager_secret" "github_token" {
  secret_id = "github-token"
  replication {
    auto {}
  }
}
resource "google_secret_manager_secret" "webhook_secret" {
  secret_id = "github-webhook-secret"
  replication {
    auto {}
  }
}

# The identity your app runs as
resource "google_service_account" "app" {
  account_id   = "pr-bot-app"
  display_name = "PR Bot Cloud Run service"
}

# Let the app read its secrets
resource "google_secret_manager_secret_iam_member" "app_secrets" {
  for_each = toset([
    google_secret_manager_secret.anthropic_key.secret_id,
    google_secret_manager_secret.github_token.secret_id,
    google_secret_manager_secret.webhook_secret.secret_id,
  ])
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.app.email}"
}

resource "google_cloud_run_v2_service" "pr_bot" {
  name     = "pr-bot"
  location = var.region
  deletion_protection=false

  template {
    service_account = google_service_account.app.email
    scaling {
      min_instance_count = 0 # scale to zero — no cost when idle
      max_instance_count = 1
    }
    containers {
      image = var.image
      ports { container_port = 8080 }
      resources {
        limits = { cpu = "1000m", memory = "512Mi" }
      }
      # inject secrets as env vars
      env {
        name = "ANTHROPIC_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.anthropic_key.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "GITHUB_WEBHOOK_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.webhook_secret.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "GITHUB_TOKEN"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.github_token.secret_id
            version = "latest"
          }
        }
      }
    }
    timeout = "300s" # generous — reviews take a while
  }
  depends_on = [google_project_service.apis]
}