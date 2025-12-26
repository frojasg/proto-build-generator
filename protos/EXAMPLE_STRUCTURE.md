# Example Proto Structure

This directory contains a realistic example of Protocol Buffer definitions modeled after Square's API. These protos demonstrate various dependency patterns and module boundaries that the proto-build-generator tool needs to handle.

## Package Structure

```
protos/
├── common/           (3 files)  - Shared value objects
├── customer/         (3 files)  - Customer management
├── catalog/          (5 files)  - Product catalog
├── operations/       (4 files)  - Business operations
├── commerce/         (2 files)  - Orders and invoices
├── payments/         (4 files)  - Payment processing
└── bookings/         (2 files)  - Appointments
```

**Total: 23 proto files across 7 packages**

## Package Dependencies

### Dependency Graph

```
common (no dependencies)
  ├── money.proto
  ├── address.proto
  └── error.proto

customer (depends on: common)
  ├── customer.proto → common/address.proto
  ├── loyalty_account.proto → customer/customer.proto
  └── loyalty_event.proto → customer/loyalty_account.proto

catalog (depends on: common)
  ├── catalog.proto
  ├── catalog_item.proto → catalog/catalog.proto
  ├── catalog_item_variation.proto → catalog/catalog_item.proto, common/money.proto
  ├── category.proto
  └── tax.proto → common/money.proto

operations (depends on: common, catalog)
  ├── location.proto → common/address.proto
  ├── team_member.proto
  ├── inventory.proto → catalog/catalog_item.proto, operations/location.proto
  └── inventory_change.proto → operations/inventory.proto

commerce (depends on: common, customer, catalog, operations)
  ├── order.proto → common/money.proto, customer/customer.proto, catalog/catalog_item.proto, operations/location.proto
  └── invoice.proto → common/money.proto, customer/customer.proto, commerce/order.proto

payments (depends on: common, customer, commerce, operations)
  ├── payment.proto → common/money.proto, customer/customer.proto, commerce/order.proto, operations/location.proto
  ├── refund.proto → common/money.proto, payments/payment.proto
  ├── dispute.proto → common/money.proto, payments/payment.proto
  └── bank_account.proto → operations/location.proto

bookings (depends on: customer, operations)
  ├── appointment.proto → customer/customer.proto, operations/location.proto, operations/team_member.proto
  └── appointment_segment.proto → bookings/appointment.proto, operations/team_member.proto
```

### Cross-Package Import Summary

| Package    | Imports From Packages                               | Import Count |
|------------|-----------------------------------------------------|--------------|
| common     | (none)                                               | 0            |
| customer   | common                                               | 1            |
| catalog    | common                                               | 2            |
| operations | common, catalog                                      | 3            |
| commerce   | common, customer, catalog, operations                | 7            |
| payments   | common, customer, commerce, operations               | 8            |
| bookings   | customer, operations                                 | 4            |

### Most Referenced Protos

1. **common/money.proto** - Referenced by 6 files (catalog, commerce, payments)
2. **common/address.proto** - Referenced by 3 files (customer, operations, payments)
3. **customer/customer.proto** - Referenced by 4 files (commerce, payments, bookings)
4. **operations/location.proto** - Referenced by 4 files (operations, commerce, payments, bookings)

## Expected Module Groupings

Based on the dependency structure, here are potential modularization strategies:

### Strategy 1: Package-Based (Simple)
Each package becomes a module:
- `common` (foundation module)
- `customer` (depends on common)
- `catalog` (depends on common)
- `operations` (depends on common, catalog)
- `commerce` (depends on common, customer, catalog, operations)
- `payments` (depends on common, customer, commerce, operations)
- `bookings` (depends on customer, operations)

**Pros:** Clear boundaries, mirrors source structure
**Cons:** 7 modules might be too granular

### Strategy 2: Domain-Based (Merged)
Group related packages:
- `square-common` (common)
- `square-catalog` (catalog)
- `square-customer` (customer)
- `square-operations` (operations)
- `square-transactions` (commerce + payments)
- `square-bookings` (bookings)

**Pros:** Fewer modules (6), logical groupings
**Cons:** transactions module has many dependencies

### Strategy 3: Dependency Layer-Based
Group by dependency depth:
- `square-foundation` (common)
- `square-core` (customer, catalog, operations)
- `square-services` (commerce, payments, bookings)

**Pros:** Minimal modules (3), clear layering
**Cons:** Loses domain context, larger artifacts

### Strategy 4: Hybrid (Recommended)
Balance granularity and dependency management:
- `square-common` (common) - shared value objects
- `square-customer` (customer) - customer domain
- `square-catalog` (catalog) - product catalog domain
- `square-operations` (operations) - business operations
- `square-commerce` (commerce, payments) - transaction processing
- `square-bookings` (bookings) - appointment scheduling

**Pros:** 6 modules, balanced size, clear domains
**Cons:** Commerce and payments merged (high coupling)

## Testing the Modularization Algorithm

This example provides good test cases for the algorithm:

1. **No dependencies**: `common` package should be identified as a foundation module
2. **Linear dependencies**: `customer.proto → loyalty_account.proto → loyalty_event.proto`
3. **Cross-package dependencies**: `order.proto` imports from 4 different packages
4. **Hub protos**: `common/money.proto` is used by many packages
5. **Package-internal dependencies**: `catalog` has internal dependency chains
6. **Varying package sizes**: From 2 files (commerce, bookings) to 5 files (catalog)

## Next Steps

Once the proto-build-generator tool is built, it should:
1. Parse all 23 proto files
2. Build the dependency graph
3. Apply the modularization algorithm
4. Generate Gradle submodules with correct dependencies
5. Validate that the generated project builds successfully with Wire
