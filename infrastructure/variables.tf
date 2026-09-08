variable "app_name" { type = string; default = "example-security" }
variable "node_count" { type = number; default = 2 }
variable "domain_name" { type = string }
variable "ssh_public_key" { type = string; sensitive = true }
variable "trusted_admin_cidr" { type = string }
variable "vm_package_id" { type = string; description = "Krystal/Katapult package ID for the chosen 2-vCPU/4-GB VM." }
variable "disk_template_id" { type = string; description = "Krystal/Katapult Ubuntu disk template ID for the selected data centre." }
