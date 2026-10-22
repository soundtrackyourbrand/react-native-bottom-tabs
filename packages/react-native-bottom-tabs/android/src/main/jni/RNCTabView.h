#pragma once

#include <ReactCommon/JavaTurboModule.h>
#include <ReactCommon/TurboModule.h>
#include <jsi/jsi.h>

// Stands in for the header codegen generates under the same name, which the
// app's autolinked registry includes. That registry only sees the descriptors
// this header declares, and codegen writes none for RNCTabView, whose spec is
// interface only.
#include <react/renderer/components/RNCTabView/RNCTabViewComponentDescriptor.h>

namespace facebook::react {

JSI_EXPORT
std::shared_ptr<TurboModule> RNCTabView_ModuleProvider(
    const std::string &moduleName,
    const JavaTurboModule::InitParams &params);

} // namespace facebook::react
