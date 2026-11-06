# This configuration models the proven Krystal topology:
#
#   Internet -> managed HTTPS LB :443 -> two VM public IPs :5173
#            -> frontend nginx :80 -> backend Spring Boot :8080 -> Atlas
#
# manage_infrastructure defaults to false so that init/validate/plan cannot
# accidentally create a second chargeable environment. For the existing live
# environment, set it true and import every resource before the first apply.

data "katapult_global_address_lists" "global" {
  count = 1
}

data "katapult_virtual_machine_packages" "available" {
  count = 1
}

data "katapult_disk_templates" "available" {
  count             = 1
  include_universal = true
}

data "katapult_virtual_machines" "existing" {
  count = 1
}

data "katapult_load_balancers" "existing" {
  count = 1
}

data "katapult_security_groups" "existing" {
  count         = 1
  include_rules = false
}

locals {
  load_balancer_address_list_ids = [
    for address_list in data.katapult_global_address_lists.global[0].address_lists :
    address_list.id if address_list.name == var.load_balancer_address_list_name
  ]
}

resource "katapult_virtual_machine_group" "app" {
  count = var.manage_infrastructure ? 1 : 0

  name      = "${var.app_name}-production"
  segregate = true

  lifecycle {
    prevent_destroy = true
  }
}

resource "katapult_ip" "app" {
  count = var.manage_infrastructure ? var.node_count : 0

  version = 4

  lifecycle {
    prevent_destroy = true
  }
}

resource "katapult_virtual_machine" "app" {
  count = var.manage_infrastructure ? var.node_count : 0

  name        = length(var.vm_names) == var.node_count ? var.vm_names[count.index] : format("%s-app-%02d", var.app_name, count.index + 1)
  hostname    = length(var.vm_names) == var.node_count ? var.vm_names[count.index] : format("%s-app-%02d", var.app_name, count.index + 1)
  description = "${var.domain_name} application node ${count.index + 1}"
  powered_on  = true

  package               = var.vm_package
  disk_template         = var.disk_template
  disk_template_options = { install_agent = "true" }
  group_id              = katapult_virtual_machine_group.app[0].id
  ip_address_ids        = [katapult_ip.app[count.index].id]
  network_speed_profile = var.network_speed_profile

  system_disk = {
    name       = "System Disk"
    size_in_gb = var.system_disk_size_gb
  }

  lifecycle {
    prevent_destroy = true

    precondition {
      condition     = trimspace(var.vm_package) != "" && trimspace(var.disk_template) != ""
      error_message = "Set vm_package and disk_template to verified Krystal permalinks or IDs before managing infrastructure."
    }
  }
}

resource "katapult_load_balancer" "app" {
  count = var.manage_infrastructure ? 1 : 0

  name                = var.domain_name
  https_redirect      = true
  virtual_machine_ids = katapult_virtual_machine.app[*].id

  lifecycle {
    prevent_destroy = true
  }
}

resource "katapult_load_balancer_rule" "https" {
  count = var.manage_infrastructure ? 1 : 0

  load_balancer_id = katapult_load_balancer.app[0].id
  protocol         = "HTTPS"
  listen_port      = 443
  destination_port = 5173
  certificate_ids  = var.certificate_ids

  passthrough_ssl = false
  backend_ssl     = false
  proxy_protocol  = false

  check_enabled       = true
  check_protocol      = "HTTP"
  check_path          = "/health"
  check_http_statuses = "2"
  check_interval      = 20
  check_timeout       = 5
  check_fall          = 2
  check_rise          = 2

  lifecycle {
    prevent_destroy = true

    precondition {
      condition     = length(var.certificate_ids) > 0
      error_message = "Supply the existing Krystal certificate ID for the public hostname."
    }
  }
}

resource "katapult_security_group" "app" {
  count = var.manage_infrastructure ? 1 : 0

  name               = var.domain_name
  associations       = katapult_virtual_machine.app[*].id
  allow_all_inbound  = false
  allow_all_outbound = true

  inbound_rules = [
    {
      action   = "allow"
      protocol = "TCP"
      ports    = "22"
      targets  = [var.trusted_admin_cidr]
      notes    = "SSH from the trusted administrator address only"
    },
    {
      action   = "allow"
      protocol = "TCP"
      ports    = "5173"
      targets  = local.load_balancer_address_list_ids
      notes    = "Frontend origin traffic from Krystal managed load balancers only"
    }
  ]

  lifecycle {
    prevent_destroy = true

    precondition {
      condition     = can(cidrhost(var.trusted_admin_cidr, 0)) && var.trusted_admin_cidr != "0.0.0.0/0"
      error_message = "trusted_admin_cidr must be a valid restricted CIDR, normally your public IPv4 address with /32; 0.0.0.0/0 is rejected."
    }

    precondition {
      condition     = length(local.load_balancer_address_list_ids) == 1
      error_message = "load_balancer_address_list_name must exactly match one Krystal global address list. Run the discovery command in README.md and use the managed-load-balancer list."
    }
  }
}

check "ha_node_count" {
  assert {
    condition     = !var.manage_infrastructure || length(katapult_virtual_machine.app) >= 2
    error_message = "At least two application VMs are required for the intended HA topology."
  }
}

check "vm_names_count" {
  assert {
    condition     = length(var.vm_names) == 0 || length(var.vm_names) == var.node_count
    error_message = "vm_names must be empty or contain exactly node_count entries."
  }
}
