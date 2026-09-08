# Production intent:
# - 2 identical application VMs
# - both in a segregated VM group
# - Krystal managed LB health-checking GET /health
# - only LB HTTP traffic reaches the VMs; SSH limited to trusted_admin_cidr
#
# Katapult IDs (package/template/network) are account/data-centre specific. Resolve
# them from the Krystal console/API before the first apply. Keep secrets outside
# Terraform state.

resource "katapult_virtual_machine_group" "app" {
  name      = "${var.app_name}-production"
  segregate = true
}

# NOTE: the provider's VM schema is evolving. The accompanying README gives the
# exact workflow: run `terraform init`, then `terraform providers schema -json`
# and map the account-specific package/template IDs before first apply.
# Keeping this block commented avoids a misleading or destructive first apply.
#
# resource "katapult_virtual_machine" "app" {
#   count                    = var.node_count
#   name                     = format("%s-app-%02d", var.app_name, count.index + 1)
#   package                  = var.vm_package_id
#   disk_template            = var.disk_template_id
#   virtual_machine_group_id = katapult_virtual_machine_group.app.id
#   user_data = templatefile("${path.module}/templates/cloud-init.yaml", {
#     hostname       = format("%s-app-%02d", var.app_name, count.index + 1)
#     ssh_public_key = var.ssh_public_key
#   })
# }
