#import "BottomSheetAccessibilityObservation.h"

#import <dlfcn.h>
#import <mach-o/dyld.h>
#import <mach-o/nlist.h>
#import <mach/mach.h>
#import <objc/runtime.h>

static BOOL BottomSheetAccessibilityObservationEnabled = NO;
static BOOL BottomSheetTestVoiceOverRunning = NO;
static BOOL BottomSheetTestDismissFocused = NO;
static NSUInteger BottomSheetLayoutChangedNotificationCount = 0;
static IMP BottomSheetOriginalAccessibilityElementIsFocused = nil;

static BOOL BottomSheetObservedVoiceOverRunning(void)
{
  if (BottomSheetAccessibilityObservationEnabled) {
    return BottomSheetTestVoiceOverRunning;
  }

  typedef BOOL (*VoiceOverRunningFunction)(void);
  static VoiceOverRunningFunction original =
      (VoiceOverRunningFunction)dlsym(RTLD_NEXT, "UIAccessibilityIsVoiceOverRunning");
  return original == nil ? NO : original();
}

static void BottomSheetObservedAccessibilityPost(
    UIAccessibilityNotifications notification,
    id argument)
{
  if (BottomSheetAccessibilityObservationEnabled) {
    if (notification == UIAccessibilityLayoutChangedNotification && argument == nil) {
      BottomSheetLayoutChangedNotificationCount++;
    }
    return;
  }

  typedef void (*AccessibilityPostFunction)(UIAccessibilityNotifications, id);
  static AccessibilityPostFunction original =
      (AccessibilityPostFunction)dlsym(RTLD_NEXT, "UIAccessibilityPostNotification");
  if (original != nil) {
    original(notification, argument);
  }
}

struct BottomSheetTestSymbolRebinding {
  const char *name;
  void *replacement;
};

static void BottomSheetRebindSection(
    const struct section_64 *section,
    intptr_t slide,
    const uint32_t *indirectSymbolTable,
    const struct nlist_64 *symbolTable,
    const char *stringTable,
    const BottomSheetTestSymbolRebinding *rebindings,
    size_t rebindingCount)
{
  uintptr_t *bindings = (uintptr_t *)(slide + section->addr);
  vm_address_t pageStart = (vm_address_t)bindings & ~((vm_address_t)vm_page_size - 1);
  vm_size_t pageLength = (vm_size_t)(section->size + ((vm_address_t)bindings - pageStart));
  pageLength = (pageLength + vm_page_size - 1) & ~((vm_size_t)vm_page_size - 1);
  vm_protect(
      mach_task_self(),
      pageStart,
      pageLength,
      false,
      VM_PROT_READ | VM_PROT_WRITE | VM_PROT_COPY);

  for (NSUInteger index = 0; index < section->size / sizeof(uintptr_t); index++) {
    uint32_t symbolIndex = indirectSymbolTable[section->reserved1 + index];
    if (symbolIndex == INDIRECT_SYMBOL_ABS || symbolIndex == INDIRECT_SYMBOL_LOCAL ||
        symbolIndex == (INDIRECT_SYMBOL_LOCAL | INDIRECT_SYMBOL_ABS)) {
      continue;
    }

    uint32_t stringOffset = symbolTable[symbolIndex].n_un.n_strx;
    const char *symbolName = stringTable + stringOffset;
    if (symbolName[0] == '_') {
      symbolName++;
    }
    for (size_t rebindingIndex = 0; rebindingIndex < rebindingCount; rebindingIndex++) {
      if (strcmp(symbolName, rebindings[rebindingIndex].name) == 0) {
        bindings[index] = (uintptr_t)rebindings[rebindingIndex].replacement;
        break;
      }
    }
  }
}

static void BottomSheetRebindImage(
    const struct mach_header *header,
    intptr_t slide,
    const BottomSheetTestSymbolRebinding *rebindings,
    size_t rebindingCount)
{
  if (header->magic != MH_MAGIC_64) {
    return;
  }

  const struct mach_header_64 *header64 = (const struct mach_header_64 *)header;
  const struct load_command *command = (const struct load_command *)(header64 + 1);
  const struct segment_command_64 *linkEdit = nil;
  const struct symtab_command *symbolCommand = nil;
  const struct dysymtab_command *dynamicSymbolCommand = nil;
  for (uint32_t index = 0; index < header64->ncmds; index++) {
    if (command->cmd == LC_SEGMENT_64) {
      const struct segment_command_64 *segment = (const struct segment_command_64 *)command;
      if (strcmp(segment->segname, SEG_LINKEDIT) == 0) {
        linkEdit = segment;
      }
    } else if (command->cmd == LC_SYMTAB) {
      symbolCommand = (const struct symtab_command *)command;
    } else if (command->cmd == LC_DYSYMTAB) {
      dynamicSymbolCommand = (const struct dysymtab_command *)command;
    }
    command = (const struct load_command *)((const char *)command + command->cmdsize);
  }
  if (linkEdit == nil || symbolCommand == nil || dynamicSymbolCommand == nil) {
    return;
  }

  uintptr_t linkEditBase = slide + linkEdit->vmaddr - linkEdit->fileoff;
  const struct nlist_64 *symbolTable =
      (const struct nlist_64 *)(linkEditBase + symbolCommand->symoff);
  const char *stringTable = (const char *)(linkEditBase + symbolCommand->stroff);
  const uint32_t *indirectSymbolTable =
      (const uint32_t *)(linkEditBase + dynamicSymbolCommand->indirectsymoff);

  command = (const struct load_command *)(header64 + 1);
  for (uint32_t commandIndex = 0; commandIndex < header64->ncmds; commandIndex++) {
    if (command->cmd == LC_SEGMENT_64) {
      const struct segment_command_64 *segment = (const struct segment_command_64 *)command;
      const struct section_64 *section = (const struct section_64 *)(segment + 1);
      for (uint32_t sectionIndex = 0; sectionIndex < segment->nsects; sectionIndex++) {
        uint32_t sectionType = section[sectionIndex].flags & SECTION_TYPE;
        if (sectionType == S_LAZY_SYMBOL_POINTERS ||
            sectionType == S_NON_LAZY_SYMBOL_POINTERS) {
          BottomSheetRebindSection(
              &section[sectionIndex],
              slide,
              indirectSymbolTable,
              symbolTable,
              stringTable,
              rebindings,
              rebindingCount);
        }
      }
    }
    command = (const struct load_command *)((const char *)command + command->cmdsize);
  }
}

__attribute__((constructor)) static void BottomSheetInstallAccessibilityRebindings(void)
{
  const BottomSheetTestSymbolRebinding rebindings[] = {
      {"UIAccessibilityIsVoiceOverRunning", (void *)BottomSheetObservedVoiceOverRunning},
      {"UIAccessibilityPostNotification", (void *)BottomSheetObservedAccessibilityPost},
  };
  for (uint32_t index = 0; index < _dyld_image_count(); index++) {
    BottomSheetRebindImage(
        _dyld_get_image_header(index),
        _dyld_get_image_vmaddr_slide(index),
        rebindings,
        sizeof(rebindings) / sizeof(rebindings[0]));
  }
}

static BOOL BottomSheetObservedAccessibilityElementIsFocused(id object, SEL selector)
{
  if (BottomSheetAccessibilityObservationEnabled &&
      [[object accessibilityLabel] isEqualToString:@"Dismiss"]) {
    return BottomSheetTestDismissFocused;
  }
  if (BottomSheetOriginalAccessibilityElementIsFocused == nil) {
    return NO;
  }
  return ((BOOL (*)(id, SEL))BottomSheetOriginalAccessibilityElementIsFocused)(object, selector);
}

static void BottomSheetInstallDismissFocusObservation(UIView *dismiss)
{
  Class dismissClass = dismiss.class;
  SEL selector = @selector(accessibilityElementIsFocused);
  Method method = class_getInstanceMethod(dismissClass, selector);
  NSCAssert(method != nil, @"Dismiss must inherit accessibilityElementIsFocused");

  IMP currentImplementation = method_getImplementation(method);
  if (currentImplementation == (IMP)BottomSheetObservedAccessibilityElementIsFocused) {
    return;
  }

  BottomSheetOriginalAccessibilityElementIsFocused = currentImplementation;
  BOOL added = class_addMethod(
      dismissClass,
      selector,
      (IMP)BottomSheetObservedAccessibilityElementIsFocused,
      method_getTypeEncoding(method));
  if (!added) {
    class_replaceMethod(
        dismissClass,
        selector,
        (IMP)BottomSheetObservedAccessibilityElementIsFocused,
        method_getTypeEncoding(method));
  }
}

@implementation BottomSheetAccessibilityObservation

+ (void)beginAccessibilityObservationForDismiss:(UIView *)dismiss
                                voiceOverRunning:(BOOL)voiceOverRunning
                                         focused:(BOOL)focused
{
  BottomSheetInstallDismissFocusObservation(dismiss);
  BottomSheetTestVoiceOverRunning = voiceOverRunning;
  BottomSheetTestDismissFocused = focused;
  BottomSheetLayoutChangedNotificationCount = 0;
  BottomSheetAccessibilityObservationEnabled = YES;
}

+ (void)resetAccessibilityNotificationCount
{
  BottomSheetLayoutChangedNotificationCount = 0;
}

+ (NSUInteger)accessibilityLayoutChangedNotificationCount
{
  return BottomSheetLayoutChangedNotificationCount;
}

+ (void)endAccessibilityObservation
{
  BottomSheetAccessibilityObservationEnabled = NO;
  BottomSheetTestVoiceOverRunning = NO;
  BottomSheetTestDismissFocused = NO;
  BottomSheetLayoutChangedNotificationCount = 0;
}

@end
