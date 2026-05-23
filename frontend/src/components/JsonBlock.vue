<script setup lang="ts">
defineProps<{
  value: unknown;
}>();
</script>

<template>
  <pre>{{ value }}</pre>
</template>
