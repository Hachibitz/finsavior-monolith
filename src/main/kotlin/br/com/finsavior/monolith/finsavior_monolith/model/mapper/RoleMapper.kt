package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.enums.RoleEnum
import br.com.olxcarwatcher.model.entity.Role

fun Set<RoleEnum>.toRoleEntities(): Set<Role> {
    return this.map { roleEnum -> Role(name = roleEnum.name) }.toSet()
}