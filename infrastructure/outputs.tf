output "management_enabled" {
  value = var.manage_infrastructure
}

output "vm_group_id" {
  value = try(katapult_virtual_machine_group.app[0].id, null)
}

output "virtual_machine_ids" {
  value = katapult_virtual_machine.app[*].id
}

output "virtual_machine_public_ips" {
  value = katapult_ip.app[*].address
}

output "load_balancer_id" {
  value = try(katapult_load_balancer.app[0].id, null)
}

output "load_balancer_ip" {
  value = try(katapult_load_balancer.app[0].ip_address, null)
}

output "security_group_id" {
  value = try(katapult_security_group.app[0].id, null)
}

output "public_url" {
  value = "https://${var.domain_name}"
}

output "post_apply_actions" {
  value = var.manage_infrastructure ? [
    "Ask Krystal Support to place the two VMs in separate London clusters; VM-group segregation covers hypervisors, not cluster placement.",
    "Install Docker and application files on each VM; provider 0.0.23 does not expose cloud-init/user-data.",
    "Install /etc/example-security/backend.env with mode 0600 on both VMs.",
    "Allow both stable VM egress IPs in MongoDB Atlas, then deploy one node at a time.",
    "Verify https://${var.domain_name}/health and a functional login before restricting or deleting the previous environment."
  ] : ["No infrastructure is managed while manage_infrastructure=false."]
}
