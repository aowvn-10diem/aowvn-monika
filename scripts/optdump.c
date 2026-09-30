#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <stdbool.h>
#include "libretro.h"

static void logcb(enum retro_log_level l, const char *fmt, ...) { (void)l; (void)fmt; }
static void print_v2(const struct retro_core_option_v2_definition *d) {
  for (; d && d->key; d++) {
    printf("OPT %s default=%s values=", d->key, d->default_value ? d->default_value : "(first)");
    for (int i = 0; i < RETRO_NUM_CORE_OPTION_VALUES_MAX && d->values[i].value; i++) printf("%s%s", i ? "|" : "", d->values[i].value);
    printf("\n");
  }
}
static void print_v1(const struct retro_core_option_definition *d) {
  for (; d && d->key; d++) {
    printf("OPT %s default=%s values=", d->key, d->default_value ? d->default_value : "(first)");
    for (int i = 0; i < RETRO_NUM_CORE_OPTION_VALUES_MAX && d->values[i].value; i++) printf("%s%s", i ? "|" : "", d->values[i].value);
    printf("\n");
  }
}
static bool env(unsigned cmd, void *data) {
  switch (cmd) {
    case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: *(unsigned *)data = 2; return true;
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2: print_v2(((struct retro_core_options_v2 *)data)->definitions); return true;
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL: { struct retro_core_options_v2_intl *i = data; if (i && i->us) print_v2(i->us->definitions); return true; }
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: { struct retro_core_options_intl *i = data; if (i && i->us) print_v1(i->us); return true; }
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS: print_v1((const struct retro_core_option_definition *)data); return true;
    case RETRO_ENVIRONMENT_SET_VARIABLES: { const struct retro_variable *v = data; for (; v && v->key; v++) printf("VAR %s = %s\n", v->key, v->value); return true; }
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: ((struct retro_log_callback *)data)->log = logcb; return true;
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: return true;
    case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION + 1000: return false;
    default: return false;
  }
}
int main(int argc, char **argv) {
  void *h = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
  if (!h) { fprintf(stderr, "dlopen lỗi: %s\n", dlerror()); return 2; }
  void (*set_env)(retro_environment_t) = dlsym(h, "retro_set_environment");
  void (*init)(void) = dlsym(h, "retro_init");
  if (!set_env) return 3;
  set_env(env);
  if (init) init();
  if (argc > 2) {
    bool (*load)(const struct retro_game_info *) = dlsym(h, "retro_load_game");
    FILE *f = fopen(argv[2], "rb"); if (!f) return 4;
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    void *buf = malloc(n); fread(buf, 1, n, f); fclose(f);
    struct retro_game_info gi = { argv[2], buf, (size_t)n, NULL };
    load(&gi);
  }
  return 0;
}
