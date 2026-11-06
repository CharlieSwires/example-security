variable "manage_infrastructure" {
  description = "Safety switch. False creates no billable infrastructure. Set true only for an import or an explicitly reviewed new deployment."
  type        = bool
  default     = false
}

variable "app_name" {
  type    = string
  default = "example-security"
}

variable "domain_name" {
  description = "Public HTTPS hostname. Used for naming/documentation; DNS remains a separate Krystal UI/API operation."
  type        = string
  default     = "charlie-example-security.co.uk"
}

variable "node_count" {
  description = "Number of identical application nodes behind the managed load balancer."
  type        = number
  default     = 2

  validation {
    condition     = var.node_count >= 2
    error_message = "The HA deployment requires at least two application nodes."
  }
}

variable "vm_names" {
  description = "Optional exact VM names/hostnames. Supply one per node when importing existing VMs to avoid an unwanted rename."
  type        = list(string)
  default     = []
}

variable "vm_package" {
  description = "Krystal VM package permalink or ID, for example the selected 2-vCPU/4-GB package. Confirm it with the provider data source before applying."
  type        = string
  default     = ""
}

variable "disk_template" {
  description = "Krystal Ubuntu disk-template permalink or ID. Confirm it with the provider data source before applying."
  type        = string
  default     = ""
}

variable "system_disk_size_gb" {
  description = "Boot disk size for each application VM."
  type        = number
  default     = 80
}

variable "network_speed_profile" {
  description = "Optional Krystal network speed profile permalink. Leave null to use the package/provider default."
  type        = string
  default     = null
  nullable    = true
}

variable "trusted_admin_cidr" {
  description = "Public administrator IPv4 CIDR allowed to use SSH, normally your current public address followed by /32."
  type        = string
  default     = ""
}

variable "load_balancer_address_list_name" {
  description = "Exact name of Krystal's global address list containing the managed load-balancer origin addresses."
  type        = string
  default     = ""
}

variable "certificate_ids" {
  description = "Existing Krystal certificate ID(s) for the public hostname. Certificates are currently created outside this configuration."
  type        = set(string)
  default     = []
}

variable "ssh_public_key" {
  description = "Retained as a sensitive operator input for bootstrap documentation. Provider 0.0.23 does not expose VM cloud-init/user-data, so Terraform does not install this key."
  type        = string
  sensitive   = true
  default     = ""
}
